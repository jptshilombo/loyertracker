# ADR-09 — Injection du contexte tenant (résolution `keycloak_id → bailleur_id` sous RLS)

| Champ | Valeur |
|-------|--------|
| Statut | Acceptée |
| Date | 2026-06-07 |
| Phase | 07 — Développement (Sprint S01) |
| Décidée par | PO jptshilombo@gmail.com (jordan) |
| Exigences couvertes | ENF-02, ADR-01 (couche RLS), EF-02/03/04/05 (invitation/délégation) |
| Stories impactées | US-11, US-12, US-13 (prérequis transverse) |

## Contexte

La RLS `FORCE` (ADR-01) masque toute ligne dont `bailleur_id ≠ app.current_bailleur_id`. Pour
servir une requête authentifiée, l'application doit donc **positionner `app.current_bailleur_id`**
au début de chaque transaction. Or l'identité portée par le JWT est le `sub` Keycloak
(`keycloak_id`), **pas** l'identifiant applicatif `bailleur.id`.

Résoudre `keycloak_id → bailleur.id` impose un `SELECT … FROM bailleur WHERE keycloak_id = ?`.
Cette lecture est **elle-même soumise à la RLS** (policy `bailleur.id = app.current_bailleur_id`) :
pour lire le bailleur il faudrait déjà connaître son `id` → **dépendance circulaire**. La même
impasse vaut pour l'**acceptation d'invitation** (US-12), non authentifiée : il faut lire
l'`invitation` par son `token` sans contexte tenant connu d'avance.

Un chemin de lecture privilégié (contournement RLS) est donc nécessaire. Le choix de ce chemin
est structurant pour la sécurité et fait l'objet du présent ADR.

## Décision

**Résolution via fonctions PostgreSQL `SECURITY DEFINER`, propriété d'un rôle `BYPASSRLS`.**

Une migration `V2` crée deux fonctions en lecture seule, `SECURITY DEFINER`, dont le propriétaire
est le rôle technique `loyertracker_batch` (déjà créé `BYPASSRLS` en V1) :

| Fonction | Rôle |
|----------|------|
| `resolve_bailleur_id(keycloak_id text) → uuid` | Mappe l'identité Keycloak vers l'`id` applicatif du bailleur. |
| `resolve_invitation_bailleur(token text) → uuid` | Renvoie le `bailleur_id` de l'invitation portant ce token (acceptation US-12). |

Exécutées sous l'identité du propriétaire `BYPASSRLS`, elles **contournent la RLS de façon étroite
et auditable** : elles ne révèlent qu'un identifiant que l'appelant possède déjà (son propre `sub`,
ou un `token` de capacité). Chaque fonction fixe `search_path = public` (durcissement injection).

Côté application, un composant transverse `securite/TenantContext` :
1. extrait le `sub` du JWT (ou le `token` pour l'acceptation) ;
2. appelle la fonction de résolution **dans la transaction** courante (`EntityManager`) ;
3. positionne `app.current_bailleur_id` via `set_config(…, is_local := true)` — donc sur la **même
   connexion** que les requêtes JPA suivantes (cf. `InscriptionService`, déjà sur ce patron).

La résolution et le `set_config` ont lieu **à l'intérieur de chaque méthode `@Transactional`** (et
non dans un filtre web), car `is_local := true` est lié à la transaction et le pool de connexions
ne garantit pas la continuité connexion↔requête hors transaction.

## Alternatives écartées

| Alternative | Raison du rejet |
|-------------|-----------------|
| **Claim `bailleur_id` dans le JWT** (attribut Keycloak + protocol mapper) | Zéro contournement RLS, mais couple le cloisonnement à la config Keycloak et impose de gérer le 1ᵉʳ token post-inscription (refresh/re-login). Réévaluable si l'on industrialise le write-back Keycloak (US-12 introduit l'Admin API). |
| **Connexion dédiée rôle `batch` (BYPASSRLS) sur le chemin requête** | Place un rôle « tout-puissant » sur chaque requête authentifiée et ajoute un 2ᵉ pool : surface d'attaque large pour un besoin de simple lecture d'un mapping. |
| **Désactiver `FORCE` / requête en superutilisateur** | Détruit la défense en profondeur (ADR-01). Inacceptable. |

## Conséquences

- ✅ Surface privilégiée minimale : **2 fonctions en lecture seule**, contournement explicite et traçable.
- ✅ Cohérent avec le patron `set_config(is_local := true)` déjà éprouvé (`InscriptionService`).
- ✅ Débloque US-11 (génération), US-12 (acceptation) et US-13 (intercepteur/`AuthorizationService`).
- ⚠️ **Pré-requis de déploiement** : le rôle qui applique les migrations doit être **membre de
  `loyertracker_batch`** (ou superutilisateur) pour réassigner la propriété des fonctions
  (`ALTER FUNCTION … OWNER TO loyertracker_batch`). Vérifié en test (Testcontainers superutilisateur) ;
  à provisionner pour staging/prod (réserve R2).
- ⚠️ Toute évolution des fonctions `SECURITY DEFINER` est un point de revue sécurité (changements
  soumis à relecture, `search_path` figé, lecture seule stricte).
