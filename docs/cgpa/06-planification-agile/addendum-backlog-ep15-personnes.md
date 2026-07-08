# Addendum Backlog — Epic EP-15 (Gestion des personnes)

| Champ | Valeur |
|-------|--------|
| Document de référence | `product-backlog.md`, `addendum-patrimoine-backlog.md`, `addendum-backlog-ep10-ep12.md`, `addendum-backlog-ep14.md` — **non modifiés** |
| Statut | **Proposé** — cadrage documentaire ; kickoff clos (K1 tranché par le PO le 2026-07-08) ; GO explicite du PO sur le Plan d'Exécution requis avant Sprint A |
| Date | 2026-07-08 |
| Décisions liées | ADR-16 (D-PERS-001) |
| Plan d'exécution | `plan-execution-ep15-personnes.md` (Sprints A/B/C) |

> **Numérotation.** US-01→104 sont déjà occupées (EP-01→14 ; EP-13 réservé « Fin de bail »,
> non développé). Ce document introduit **US-105 à US-114** sous l'epic **EP-15**, sans
> modifier aucun backlog déjà validé.

---

## EP-15 — Gestion des personnes

| ID | Epic | Jalons | Priorité |
|----|------|--------|----------|
| EP-15 | **Gestion des personnes** — Gestionnaires et Locataires comme entités durables (statuts, historique, audit, anti-doublons), indépendantes des Baux/Affectations | Post-`1.9.0` (Sprints A/B/C) | Must |

### Sprint A — Gestionnaire (statut global, cycle de vie)

### US-105 — Profil et cycle de vie du compte Gestionnaire (créer/modifier/suspendre/réactiver)

**En tant que** bailleur, **je veux** compléter le profil d'un Gestionnaire et faire évoluer son
statut (suspendre/réactiver) **afin de** gérer la relation au-delà de la simple affectation à un
bien.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un Gestionnaire lié par invitation à ce bailleur **W** le bailleur complète/modifie son profil (téléphone, photo, observations) **T** les champs sont persistés sur le compte **global** (partagé, ADR-16 D1), une entrée `audit_log` (`MODIFIER_GESTIONNAIRE`) est créée. **G** un Gestionnaire `ACTIVE` **W** le bailleur le suspend **T** statut `SUSPENDU` immédiat sans pré-condition, compte Keycloak `enabled=false`, audit `SUSPENDRE_GESTIONNAIRE`. **G** un Gestionnaire `SUSPENDU` **W** le bailleur le réactive **T** statut `ACTIVE`, Keycloak `enabled=true`, audit `REACTIVER_GESTIONNAIRE`. **G** un Gestionnaire **W** un autre Gestionnaire tente une de ces actions **T** 403 (seul `BAILLEUR` administre, RM-107). |
| Dépendances | ADR-16 acceptée ; K1 tranché ✅ (2026-07-08, sémantique « créer » = profil sur compte existant) |
| Priorité | Must |
| Points | 5 |
| Risques | Profil partagé mutable par tout bailleur en relation (RSV-EP15-01) |
| Source | Besoin PO ; ADR-16 D1 ; EF-97 |

### US-106 — Archivage et restauration du Gestionnaire (contrainte cross-tenant)

**En tant que** bailleur, **je veux** archiver un Gestionnaire seulement s'il n'a plus aucune
affectation active nulle part **afin de** ne jamais casser silencieusement une relation active
d'un autre bailleur.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un Gestionnaire sans aucune `Affectation` `ACTIVE`, tous bailleurs confondus **W** le bailleur l'archive **T** statut `ARCHIVE`, Keycloak désactivé, historique/audits/affectations passées conservés, audit `ARCHIVER_GESTIONNAIRE`. **G** un Gestionnaire avec une `Affectation` `ACTIVE` chez **un autre** bailleur (invisible sous RLS pour le bailleur courant) **W** l'archivage est tenté **T** rejeté 409 via la fonction `SECURITY DEFINER gestionnaire_a_affectation_active` (ADR-16 D4) — test cross-tenant dédié. **G** un Gestionnaire `ARCHIVE` **W** restauration **T** statut `ACTIVE`, Keycloak réactivé, aucune affectation recréée automatiquement, audit `RESTAURER_GESTIONNAIRE`. |
| Dépendances | US-105 |
| Priorité | Must |
| Points | 8 |
| Risques | Fonction `SECURITY DEFINER` mal bornée pourrait exposer plus qu'un booléen — revue sécurité dédiée avant Gate Staging |
| Source | Règle métier PO ; ADR-16 D1/D4 ; EF-98/99 |

### US-107 — Recherche multicritère et détection de doublons Gestionnaire

**En tant que** bailleur, **je veux** rechercher un Gestionnaire et être averti d'un doublon
probable **afin d'** éviter les fiches redondantes.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** une recherche par nom/téléphone/email **W** exécutée **T** résultats paginés, scope = Gestionnaires en relation (affectation active ou passée) avec ce bailleur. **G** un email ou téléphone déjà présent dans ce périmètre **W** création/modification de profil **T** avertissement (liste des correspondances), création possible après confirmation explicite (pas de rejet automatique). |
| Dépendances | US-105 |
| Priorité | Should |
| Points | 3 |
| Risques | Faux positifs de doublon sur téléphone partagé (foyer) — avertissement non bloquant, jamais un rejet |
| Source | Besoin PO ; EF-102/103 |

### US-108 — Historique Gestionnaire

**En tant que** bailleur, **je veux** consulter la chronologie complète d'un Gestionnaire
**afin de** comprendre la relation dans le temps.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un Gestionnaire **W** le bailleur ouvre son historique **T** chronologie des créations/modifications de profil, changements de statut, affectations passées et actives — **uniquement celles où ce bailleur a lui-même une relation** (aucune fuite d'informations sur les relations avec des tiers, cf. RSV-EP15-01). |
| Dépendances | US-105, US-106 |
| Priorité | Should |
| Points | 3 |
| Risques | Fuite d'information sur les relations d'un Gestionnaire avec d'autres bailleurs — test de non-fuite dédié |
| Source | Besoin PO ; EF-104 |

### Sprint B — Locataire (nouvelle entité, V23 additive)

### US-109 — Entité Locataire : création, modification (V23)

**En tant que** bailleur, **je veux** créer et modifier des fiches Locataire indépendantes du
bail **afin de** conserver leur identité au-delà d'un contrat unique.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un bailleur authentifié **W** il crée un Locataire (nom, prénom, téléphone, email, profession, date de naissance, type/numéro de pièce d'identité, photo optionnelle, contact d'urgence, observations) **T** l'entité est persistée avec `bailleur_id`, RLS `bailleur_isolation` (V23), statut `ACTIVE` par défaut, audit `CREER_LOCATAIRE`. **G** un Locataire de ce bailleur **W** modification **T** champs mis à jour, audit `MODIFIER_LOCATAIRE`. **G** un autre bailleur **W** tente de lire/modifier **T** RLS bloque (test cross-tenant). |
| Dépendances | Migration V23 (ADR-16 D3) |
| Priorité | Must |
| Points | 8 |
| Risques | Aucune régression attendue (additif) ; volumétrie `photo` bytea à surveiller au préflight (précédent `quittance.pdf`) |
| Source | Besoin PO ; ADR-16 D2/D3 ; EF-100 |

### US-110 — Archivage et restauration du Locataire

**En tant que** bailleur, **je veux** archiver un Locataire sans le supprimer **afin de**
conserver tout son historique tout en l'excluant des nouveaux baux.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un Locataire **W** le bailleur l'archive **T** statut `ARCHIVE`, audit `ARCHIVER_LOCATAIRE`, historique/paiements/garanties/quittances/audits conservés. **G** un Locataire `ARCHIVE` **W** sélection pour un nouveau bail **T** rejeté (409). **G** un Locataire `ARCHIVE` **W** restauration **T** statut `ACTIVE`, de nouveau sélectionnable, audit `RESTAURER_LOCATAIRE`. |
| Dépendances | US-109 |
| Priorité | Must |
| Points | 5 |
| Risques | Aucun garde-fou d'affectation active requis (contrairement au Gestionnaire) — le besoin PO ne l'exige pas pour le Locataire |
| Source | Règle métier PO ; EF-100 |

### US-111 — Recherche multicritère et détection de doublons Locataire

**En tant que** bailleur, **je veux** rechercher un Locataire et être averti d'un doublon
probable **afin d'** éviter les fiches redondantes.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** une recherche par nom/téléphone/email/numéro de pièce d'identité **W** exécutée **T** résultats paginés, scopés RLS à ce bailleur. **G** un email, téléphone ou numéro de pièce d'identité déjà présent **W** création **T** avertissement (liste des correspondances), création possible après confirmation explicite. |
| Dépendances | US-109 |
| Priorité | Should |
| Points | 3 |
| Risques | Faux positifs sur téléphone partagé (foyer) — avertissement non bloquant |
| Source | Besoin PO ; EF-102/103 |

### US-112 — Historique Locataire

**En tant que** bailleur, **je veux** consulter la chronologie complète d'un Locataire **afin
de** retrouver tous ses baux, paiements, garanties et quittances.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un Locataire **W** le bailleur ouvre son historique **T** chronologie de ses baux successifs, paiements, garanties, quittances, statuts d'archive/restauration — scope RLS. |
| Dépendances | US-109, US-110 |
| Priorité | Should |
| Points | 3 |
| Risques | Aucun (lecture agrégée sur des données déjà RLS-scopées) |
| Source | Besoin PO ; EF-105 |

### Sprint C — Bascule Bail → Locataire (V24 non additive) & RGPD

### US-113 — Bascule Bail → Locataire (migration V24)

**En tant que** bailleur, **je veux** que chaque bail référence un Locataire structuré au lieu
d'un champ texte libre **afin d'** avoir une donnée fiable et exploitable.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** tous les baux existants **W** la migration V24 s'exécute **T** un `Locataire` est créé par bail historique (`nom` = valeur intégrale de `locataire_nom`, `prenom` vide — aucun découpage automatique, RSV-EP15-02), `bail.locataire_id` peuplé à 100 %, puis rendu `NOT NULL` et `locataire_nom`/`locataire_email` supprimés. **G** un nouveau bail **W** créé/modifié **T** exige un `locataireId` existant de ce bailleur, non archivé (404/403/409 selon le cas). **G** cette migration **W** exécutée en Production **T** un backup post-migration est vérifié disponible avant tout arrêt (RSV-EP15-03, rollback applicatif non viable pour cette étape). |
| Dépendances | US-109 ; bake-in de V23 en Staging puis Production avant ce sprint |
| Priorité | Must |
| Points | 8 |
| Risques | RSV-EP15-02 (parsing nom/prénom imprécis, accepté) ; RSV-EP15-03 (migration non additive, rollback par backup uniquement) ; rupture du contrat HTTP existant de `Bail` à documenter/communiquer |
| Source | ADR-16 D3 ; EF-101 |

### US-114 — Adaptation de l'effacement RGPD au Locataire indépendant

**En tant que** bailleur, **je veux** que l'effacement RGPD d'un Locataire couvre tout son
historique de baux **afin de** répondre correctement au droit à l'effacement.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un Locataire **W** le bailleur demande son effacement RGPD **T** tous ses champs personnels (nom, prénom, email, téléphone, date de naissance, pièce d'identité, photo, contact d'urgence, observations) sont anonymisés en une seule opération, pour tous ses baux historiques rattachés. **G** la suite de tests RGPD existante (export, effacement) **W** elle s'exécute après cette bascule **T** aucune régression (contrat d'export inchangé, seule la cible de l'anonymisation change de `Bail` à `Locataire`). |
| Dépendances | US-113 |
| Priorité | Must |
| Points | 5 |
| Risques | Régression sur les tests RGPD existants si la ré-écriture de `RgpdService`/`Bail.anonymiserLocataire()` n'est pas rigoureusement équivalente au comportement actuel pour les cas déjà couverts |
| Source | ADR-16 D6 ; EF-106 |

---

## Récapitulatif & priorisation

| Sprint | Stories | Points | Priorité dominante |
|--------|---------|--------|---------------------|
| A — Gestionnaire | US-105, US-106, US-107, US-108 | 19 | Must (105/106), Should (107/108) |
| B — Locataire (V23 additive) | US-109, US-110, US-111, US-112 | 19 | Must (109/110), Should (111/112) |
| C — Bascule (V24) & RGPD | US-113, US-114 | 13 | Must |
| **Total EP-15** | 10 US | **51** | — |

## Dépendances & risques (synthèse)

- **K1 (ADR-16)** ✅ tranché par le PO le 2026-07-08 : « créer » un Gestionnaire = profil sur
  compte existant. Seul le GO explicite du PO sur le Plan d'Exécution reste requis avant
  Sprint A.
- **RSV-EP15-01** (portée globale du statut Gestionnaire, risque cross-bailleur) : accepté par
  le PO, mitigé par l'audit (US-105/106) et le scope restreint de l'historique (US-108).
- **RSV-EP15-02** (parsing nom/prénom imprécis à la migration) : accepté, correction manuelle
  possible a posteriori, sans impact fonctionnel bloquant.
- **RSV-EP15-03** (V24 non additive, rollback par backup uniquement) : Sprint C isolé
  délibérément après bake-in de V23, Préflight renforcé exigé.
- **RSV-EP15-04** (asymétrie `BienService.archiver()`) : hors périmètre EP-15, non traitée ici.
- Sprint C ne peut démarrer qu'après confirmation que V23 (Sprint B) est stable en Production
  depuis au moins un cycle de release complet (aucune anomalie remontée) — critère GO explicite
  au Plan d'Exécution.
