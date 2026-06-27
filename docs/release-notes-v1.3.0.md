# Release Notes — LoyerTracker `1.3.0`

| Champ | Valeur |
|---|---|
| Version | `1.3.0` |
| Date de release | À renseigner après `PRODUCTION_DEPLOYED` |
| Type | Release fonctionnelle (MINOR) — Sprint 4 UI Patrimoine |
| Commit applicatif | `a42d860d5a10b80b85d5a94d79c3680ef06bacdc` |
| Tag candidat | `sha-a42d860d` |
| Release précédente | `1.2.1` — `sha-47172297` (2026-06-27) |

---

## Nouvelles fonctionnalités

### Section A — Affectation d'un gestionnaire au niveau patrimoine

**Contexte :** jusqu'à `1.2.1`, un bailleur ne pouvait affecter un gestionnaire qu'au niveau
d'un bien individuel. Sprint 4 introduit l'affectation au niveau du patrimoine (portefeuille),
couvrant tous les biens de ce patrimoine en un seul acte.

**Fonctionnalités livrées :**
- Interface bailleur pour créer une affectation patrimoine (Section A du tableau de bord) :
  sélecteur de patrimoine, de gestionnaire, type et montant d'honoraires, date de début/fin.
- Affichage de l'affectation active (gestionnaire, honoraires, dates) avec action de révocation.
- Historique des affectations patrimoine : `GET /api/patrimoines/{id}/affectations` (correctif
  É-01, endpoint manquant détecté et corrigé en Staging).
- Scope strict : chaque bailleur ne voit que ses propres patrimoines et affectations (isolation
  par `@authz.peutAccederPatrimoine`).

### Section B — Exceptions fines INCLUSION/EXCLUSION par bien

**Contexte :** lorsqu'un patrimoine entier est affecté à un gestionnaire, des cas particuliers
peuvent requérir d'inclure ou d'exclure certains biens individuellement.

**Fonctionnalités livrées :**
- Interface bailleur pour configurer une exception `INCLUSION` ou `EXCLUSION` sur un bien
  individuel d'un patrimoine déjà affecté (Section B du tableau de bord).
- `EXCLUSION` : soustrait précisément ce bien de l'affectation patrimoine du gestionnaire,
  sans affecter les autres biens du portefeuille. Requiert une affectation patrimoine ACTIVE
  du même gestionnaire (RS-04).
- `INCLUSION` : accorde explicitement l'accès à un bien déjà affecté (idempotent, tolérée).
- Formulaire conditionné : la Section B n'apparaît que si une affectation patrimoine ACTIVE
  existe pour le gestionnaire sélectionné.

---

## Corrections

### É-01 — Endpoint `GET /api/patrimoines/{id}/affectations` manquant

**Problème :** le service Angular `s02-api.service.ts` appelait
`GET /api/patrimoines/{patrimoineId}/affectations` pour afficher l'historique des affectations
au niveau patrimoine, mais cet endpoint n'existait pas côté backend (404 systématique),
rendant la Section A non fonctionnelle en Staging.

**Correction :** ajout de l'endpoint complet en trois couches :
- `AffectationRepository.findByPatrimoineIdOrderByDateDebutDesc(UUID patrimoineId)`
- `AffectationService.historiquePatrimoine(UUID patrimoineId, Jwt jwt)` — avec vérification
  d'existence du patrimoine (404) avant requête
- `AffectationController` : `GET /api/patrimoines/{patrimoineId}/affectations` sécurisé par
  `@PreAuthorize("hasRole('BAILLEUR') and @authz.peutAccederPatrimoine(...)")`

**Tests ajoutés :** `historiqueAffectationsPatrimoineScopeParBailleur` — couvre le cas normal
(200 + 1 affectation ACTIVE), le bailleur tiers (403) et le patrimoine inexistant (403 via
`peutAccederPatrimoine`).

---

## Améliorations sécurité et CI

### Remédiation audit CGPA v5.4.1

- `npm audit` : 3 alertes High éliminées par mise à jour Angular DevKit/CLI `20.3.30` et
  override ciblé `http-proxy-middleware 3.0.7`. Résidu : 5 Moderate, 5 Low dans la chaîne
  de build uniquement (pas dans le bundle applicatif).
- CI Frontend : correction du clone superficiel (`fetch-depth: 0`) qui empêchait le blame
  SonarQube Frontend de calculer fiablement les nouvelles lignes de code.
- Test de confirmation archivage bien ajouté (`test(frontend)` — `91da041`).

---

## Périmètre technique

- **Backend Java** : 1 endpoint GET en lecture seule ajouté ; 3 fichiers modifiés
  (`AffectationRepository`, `AffectationService`, `AffectationController`) + 1 test
  d'intégration étendu (`S02BiensBauxAffectationsIntegrationTest` : 10 → 11 tests).
- **Frontend Angular** : nouvelle Section A (affectation patrimoine) et Section B (exceptions
  fines) dans `dashboard.component.ts/html/scss` ; modèle `s02-api.service.ts` étendu.
- **Flyway inchangé** : le rang maximal reste V15 (15 migrations) — identique à `1.2.1`.
- **API backend** : 1 endpoint ajouté (`GET /api/patrimoines/{id}/affectations`) ;
  aucun endpoint existant modifié ou supprimé.
- **Compose inchangé** : aucune variable d'environnement, réseau ou volume modifié.
- **Keycloak inchangé** : aucune modification de configuration ou de realm.

---

## Compatibilité et rollback

- **Rétrocompatibilité** : totale. L'endpoint É-01 est un ajout pur ; aucune rupture de
  contrat API ni de schéma.
- **Rollback** : retour à `sha-47172297` (`1.2.1`) par simple redéploiement des services
  `api` et `nginx` — aucun `pg_restore` requis (aucune migration ajoutée entre `1.2.1` et
  `1.3.0`).
  Commande : `LOYERTRACKER_TAG=sha-47172297 docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d api nginx`

---

## Déploiement Production

| Étape | Statut |
|---|---|
| Gate Staging Sprint 4 | ✅ GO (2026-06-27, sha-a42d860d, E6 PASS) |
| Gate Production `1.3.0` | ✅ GO sous réserve — `PRODUCTION_READY` |
| Préflight + backup | À exécuter |
| Déploiement technique (`api` + `nginx`) | À exécuter |
| Validation finale (smoke 47/0) | À exécuter |
| `PRODUCTION_DEPLOYED` | Non atteint |
| Opérateur | À renseigner |
| Hôte | `loyertracker-prod-server` (`https://loyertracker.loyerpro.org`) |
