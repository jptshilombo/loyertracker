# Addendum CDC — Gestion des personnes (Gestionnaires & Locataires durables)

| Champ | Valeur |
|-------|--------|
| Document de référence | `cahier-des-charges.md` (✅ Validé — Gate 3 Go, 2026-06-04) — **non modifié** |
| Document de référence | `dossier-architecture.md` (✅ Validé — Gate 4 Go, 2026-06-04) — étendu §3.5 (additif) |
| Statut de l'addendum | **Proposé** — cadrage documentaire ; aucun codage ni migration SQL engagé. Point de kickoff K1 (ADR-16) à trancher avant Sprint 1 |
| Date | 2026-07-08 |
| Décision liée | D-PERS-001 / ADR-16 |
| Base besoin | `docs/cgpa/02-expression-besoin/addendum-personnes.md` (BF-97→BF-101) |

---

## 1. Exigences fonctionnelles détaillées (addendum)

### 1.1 Module Gestionnaire enrichi *(étend EP-02 existant)*

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|------------------------|----------|--------|
| EF-97 | Cycle de vie du profil Gestionnaire | ED un bailleur ayant une relation (affectation active ou passée) avec un Gestionnaire · Q il crée/complète le profil (téléphone, photo, observations), le modifie, le suspend, le réactive · A les champs sont persistés sur le compte **global** ; `SUSPENDU` ne nécessite aucune pré-condition et désactive le compte Keycloak (`enabled=false`) immédiatement ; `RESTAURER` (depuis `SUSPENDU`) le réactive immédiatement. | Must | BF-97 |
| EF-98 | Archivage Gestionnaire conditionné à l'absence d'affectation active partout | ED un Gestionnaire · Q le bailleur tente de l'archiver · A la requête est acceptée **seulement si aucune `Affectation` `ACTIVE` n'existe, tous bailleurs confondus** (vérifié par une fonction `SECURITY DEFINER` dédiée, ADR-16 D4) ; sinon rejetée en 409 (cohérent avec `PatrimoineService.archiver()`, RS-06). Un Gestionnaire archivé conserve historique, audits et affectations passées ; ne peut plus se connecter ni recevoir de nouvelle affectation. | Must | BF-97 |
| EF-99 | Restauration d'un Gestionnaire archivé | ED un Gestionnaire `ARCHIVE` · Q le bailleur le restaure · A le statut repasse à `ACTIVE`, le compte Keycloak est réactivé ; aucune affectation n'est recréée automatiquement (une nouvelle affectation reste un acte explicite du bailleur, EF-23 existant inchangé). | Should | BF-97 |

### 1.2 Module Locataire *(nouveau)*

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|------------------------|----------|--------|
| EF-100 | Gestion du Locataire (CRUD + cycle de vie) | ED un bailleur authentifié · Q il crée/modifie/archive/restaure un Locataire · A l'entité est persistée, rattachée à son `bailleurId`, visible uniquement par lui (RLS, ADR-01) ; un Locataire `ARCHIVE` **ne peut plus être sélectionné** pour un nouveau bail mais reste consultable avec tout son historique (baux, paiements, garanties, quittances, audits). | Must | BF-98 |
| EF-101 | Rattachement du Bail à un Locataire (remplace le texte libre) | ED un bailleur · Q il crée ou modifie un bail · A le bail référence un `locataireId` existant de ce bailleur (FK, remplace `locataire_nom`/`locataire_email` en texte libre — bascule V25) ; un `locataireId` d'un autre bailleur est rejeté (404/403), un Locataire `ARCHIVE` est rejeté (409). Un même Locataire peut avoir plusieurs baux successifs ; jamais plusieurs baux actifs simultanés sur le même bien (déjà garanti par `uq_bail_actif`, EF-12 — aucune action requise). | Must | BF-98 |

### 1.3 Recherche & anti-doublons *(nouveau, transverse)*

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|------------------------|----------|--------|
| EF-102 | Recherche multicritère | ED un bailleur · Q il recherche un Locataire (nom, téléphone, email, numéro de pièce d'identité) ou un Gestionnaire (nom, téléphone, email) · A les résultats sont scopés à son périmètre (RLS pour Locataire ; relation d'affectation, active ou passée, pour Gestionnaire) et retournés paginés. | Should | BF-99 |
| EF-103 | Détection de doublons à la création | ED un bailleur · Q il crée un Locataire ou un Gestionnaire dont l'email, le téléphone ou (Locataire) le numéro de pièce d'identité correspond à une entrée existante dans son périmètre · A un **avertissement** est renvoyé (liste des correspondances potentielles) **sans bloquer** la création ; le bailleur confirme explicitement pour créer malgré l'avertissement. | Should | BF-100 |

### 1.4 Historique *(nouveau, transverse)*

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|------------------------|----------|--------|
| EF-104 | Écran Historique Gestionnaire | ED un bailleur · Q il consulte l'historique d'un Gestionnaire · A affichage chronologique : créations/modifications de profil, changements de statut (suspension/réactivation/archivage/restauration), affectations passées et actives (tous les bailleurs concernés **si** le bailleur courant y a lui-même une relation — pas de fuite d'informations sur des relations avec des tiers, cf. RSV-EP15-01). | Should | BF-101 |
| EF-105 | Écran Historique Locataire | ED un bailleur · Q il consulte l'historique d'un Locataire · A affichage chronologique : baux successifs, paiements, garanties, quittances, statuts d'archive/restauration — scopé RLS à ce bailleur. | Should | BF-101 |

### 1.5 RGPD & audit *(non-régression, étend ADR-03)*

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|------------------------|----------|--------|
| EF-106 | Effacement RGPD ciblant le Locataire (remplace le ciblage `Bail`) | ED un Locataire · Q le bailleur demande son effacement RGPD · A tous ses champs personnels (nom, prénom, email, téléphone, date de naissance, pièce d'identité, photo, contact d'urgence, observations) sont anonymisés en une seule opération, pour **tous ses baux historiques** ; les tests d'export/effacement RGPD existants restent verts (non-régression, ADR-16 D6). | Must | ADR-16 D6 |
| EF-107 | Audit des opérations sur Gestionnaire/Locataire | ED toute création/modification/suspension/réactivation/archivage/restauration · Q l'opération s'exécute · A une entrée `audit_log` est créée (`AuditService.enregistrer`), sans impact sur les points d'audit existants (quittance/garantie/bail-RGPD/honoraire/paiement). | Must | ADR-16 D7 |

---

## 2. Exigences non fonctionnelles (addendum)

| ID | Catégorie | Exigence | Critère d'acceptation | Source |
|----|-----------|----------|-----------------------|--------|
| ENF-91 | Sécurité — Vérification cross-tenant à surface minimale | Le pré-check « aucune affectation `ACTIVE` nulle part » (EF-98) passe **exclusivement** par une fonction SQL `SECURITY DEFINER` ne renvoyant qu'un booléen — jamais par une levée générale de la RLS sur `affectation` (`BYPASSRLS` sur le rôle applicatif). | ED un test dédié · Q on tente de lire des colonnes d'`affectation` d'un autre bailleur via un chemin détourné de cette fonction · A échec (RLS toujours active hors de cette fonction unique). | ADR-16 D4 |
| ENF-92 | RGPD — Nouvelle donnée sensible (pièce d'identité) | Le `numero_piece_identite` est traité avec la même sensibilité que les autres PII (registre des traitements, anonymisation EF-106, jamais exposé hors du périmètre RLS du bailleur). | ED le registre des traitements · Q il est mis à jour · A `numero_piece_identite` y figure explicitement. | ADR-16, ADR-03 |

---

## 3. Registre des règles métier (RM) — *complète le registre ouvert par RM-90→99 (Patrimoine)*

| ID | Règle métier | Exigence(s) liée(s) |
|----|--------------|----------------------|
| RM-100 | Un Gestionnaire a un statut `ACTIVE`\|`SUSPENDU`\|`ARCHIVE` **global**, partagé par tous les bailleurs qui l'emploient (décision PO 2026-07-08, ADR-16 D1). | EF-97 |
| RM-101 | Un Gestionnaire ne peut être archivé que si aucune `Affectation` `ACTIVE` n'existe, tous bailleurs confondus. | EF-98 |
| RM-102 | Un `SUSPENDU` n'a aucune pré-condition ; il est immédiatement restaurable. | EF-97 |
| RM-103 | Un Locataire appartient à un seul bailleur (`bailleur_id NOT NULL`, RLS `bailleur_isolation`). | EF-100 |
| RM-104 | Un Locataire peut avoir plusieurs baux successifs dans le temps ; jamais plusieurs baux actifs simultanément sur le même bien (= EF-12/`uq_bail_actif`, déjà couvert). | EF-101 |
| RM-105 | Un Locataire `ARCHIVE` ne peut plus être sélectionné pour un nouveau bail, mais reste consultable avec tout son historique. | EF-100 |
| RM-106 | Ni le Gestionnaire ni le Locataire ne sont supprimés physiquement — toute suppression est logique (changement de statut). | EF-97, EF-100 |
| RM-107 | Un Gestionnaire n'administre jamais un autre Gestionnaire ; seul le rôle `BAILLEUR` crée/modifie/suspend/archive/restaure. | EF-97, EF-100 |

---

## 4. Modèle de données (proposition logique — détail complet des migrations dans ADR-16 D3/D4)

### 4.1 Diagramme logique mis à jour

```
Bailleur (1) ───< (N) Locataire (RLS bailleur_id)
    │                    │
    │                    └───< (N) Bail [locataire_id FK, remplace locataire_nom/email]
    │
    └───< (N) Bail (1) ─── (1) Bien

Gestionnaire (global, hors RLS) ───< (N) Affectation >─── (N) Bailleur
    (statut ACTIVE|SUSPENDU|ARCHIVE, global)   (statut ACTIVE|REVOQUEE|EXPIREE, par bailleur — inchangé)
```

> **Lecture** : `Locataire` suit exactement le pattern `bailleur_id`+RLS déjà utilisé par
> `Patrimoine`/`Bien`/`Bail`/`Affectation` (ADR-01). `Gestionnaire` reste hors RLS (inchangé,
> ADR-01) mais gagne un statut de compte global, orthogonal au périmètre d'accès par bailleur
> porté par `Affectation`.

### 4.2 Nouvelles entités / attributs (proposition)

| Entité | Nouveauté | Détail |
|--------|-----------|--------|
| `Locataire` *(nouvelle)* | Entité complète | `id` (UUID), `bailleurId` (UUID NOT NULL — RLS), `nom`, `prenom`, `telephone`, `email`, `profession`, `dateNaissance`, `typePieceIdentite`, `numeroPieceIdentite`, `photo` (bytea, nullable), `contactUrgence`, `observations`, `statut` (`ACTIVE`\|`ARCHIVE`), `dateCreation`, `dateArchivage` (nullable) |
| `Gestionnaire` | `statut` *(nouveau)* | `ACTIVE`\|`SUSPENDU`\|`ARCHIVE`, `DEFAULT ACTIVE` — global, sans `bailleurId` (inchangé) |
| `Gestionnaire` | `telephone`, `photo`, `observations`, `dateCreation`, `dateSuspension`, `dateArchivage` *(nouveaux)* | Profil enrichi, partagé entre bailleurs (cf. RSV-EP15-01) |
| `Bail` | `locataireId` *(nouveau, FK)* | Remplace `locataireNom`/`locataireEmail` (texte libre) après bascule V25 |

### 4.3 Cardinalités & contraintes d'intégrité (conceptuelles)

| Règle | Contrainte conceptuelle |
|-------|--------------------------|
| RM-103 | `locataire.bailleur_id NOT NULL` + policy RLS `bailleur_isolation` FORCE (pattern V1/V12/V20) |
| RM-104 (= EF-12 existant) | Inchangé : `CREATE UNIQUE INDEX uq_bail_actif ON bail (bien_id) WHERE statut = 'ACTIF';` — aucune contrainte supplémentaire requise, la FK `locataire_id` n'affecte pas cette cardinalité |
| RM-101 | Vérifiée applicativement via la fonction `SECURITY DEFINER gestionnaire_a_affectation_active(gestionnaire_id)` (ADR-16 D4), pas par une contrainte SQL déclarative (la vérification traverse la RLS d'`affectation`, ce qu'une contrainte `CHECK`/FK ne peut pas faire) |
| RM-105 | Application-level : le endpoint de création de bail rejette (409) un `locataireId` au statut `ARCHIVE` |
| Bascule Bail | `bail.locataire_id` nullable en V24 (transition), `NOT NULL` en V25 après backfill à 100 % |

### 4.4 Index de performance (proposition)

`locataire(bailleur_id)`, `locataire(bailleur_id, statut)` (liste des locataires actifs
sélectionnables), `bail(locataire_id)` — pour préserver ENF-06 (< 2 s / 50 biens) malgré la
jointure supplémentaire `bail → locataire`.

### 4.5 Impact migration base de données *(narratif — détail complet ADR-16 D3)*

> **Renumérotation actée au Sprint B (2026-07-08)** : V23 a été consommée par le seul
> Gestionnaire (Sprint A, livrée) ; Locataire est donc porté par **V24**, et la bascule par
> **V25**.

1. **V23 (additive, Sprint A, livrée)** : colonnes `gestionnaire.statut`/`telephone`/`photo`/
   `date_creation`/`date_suspension`/`date_archivage`/`observations`.
2. **V24 (additive, Sprint B)** : table `locataire` + RLS ; `bail.locataire_id` nullable.
   Rollback applicatif seul viable.
3. **V25 (non additive, Sprint C, sprint séparé après bake-in de V24)** : backfill 1
   `Locataire` par `bail` historique (`nom = locataire_nom`, `prenom` vide — pas de découpage
   automatique fiable depuis un champ unique historique) ; `bail.locataire_id NOT NULL` ;
   suppression de `bail.locataire_nom`/`locataire_email`. **Rollback applicatif non viable** —
   restauration de backup requise (RSV-EP15-03, même profil que V20/RSV-S9-03). Préflight de
   cette release à renforcer en conséquence (backup post-migration vérifié disponible avant
   tout arrêt).

---

## 5. Contrats d'API impactés (proposition — non implémentée)

| Endpoint (proposé) | Méthode | Description | Sécurité |
|---------------------|---------|--------------|----------|
| `/api/gestionnaires` | GET | Liste/recherche multicritère (nom/téléphone/email) | BAILLEUR ; scope = Gestionnaires avec relation (affectation active ou passée) à ce bailleur |
| `/api/gestionnaires/{id}` | GET/PUT | Détail / modification du profil (téléphone, photo, observations) | BAILLEUR (relation avec ce Gestionnaire) |
| `/api/gestionnaires/{id}/suspension` | POST | Suspendre (immédiat, sans pré-condition) | BAILLEUR (relation) |
| `/api/gestionnaires/{id}/reactivation` | POST | Réactiver depuis `SUSPENDU` | BAILLEUR (relation) |
| `/api/gestionnaires/{id}/archivage` | POST | Archiver (409 si affectation `ACTIVE` ailleurs, tous bailleurs) | BAILLEUR (relation) |
| `/api/gestionnaires/{id}/restauration` | POST | Restaurer depuis `ARCHIVE` | BAILLEUR (relation) |
| `/api/gestionnaires/{id}/historique` | GET | Chronologie (statuts, affectations visibles par ce bailleur) | BAILLEUR (relation) |
| `/api/gestionnaires/verification-doublon` | GET | Correspondances potentielles (email/téléphone) | BAILLEUR |
| `/api/locataires` | GET/POST | Liste/recherche multicritère / création | BAILLEUR ; périmètre = ses Locataires (RLS) |
| `/api/locataires/{id}` | GET/PUT | Détail / modification | BAILLEUR seul (RLS) |
| `/api/locataires/{id}/archivage` | POST | Archiver (aucune pré-condition métier requise par le besoin PO) | BAILLEUR seul |
| `/api/locataires/{id}/restauration` | POST | Restaurer depuis `ARCHIVE` | BAILLEUR seul |
| `/api/locataires/{id}/historique` | GET | Chronologie (baux, paiements, garanties, quittances) | BAILLEUR seul |
| `/api/locataires/verification-doublon` | GET | Correspondances potentielles (email/téléphone/pièce d'identité) | BAILLEUR |
| `/api/biens/{id}/baux` *(existant, étendu)* | POST/PUT | Création/modification d'un bail | Ajoute `locataireId` obligatoire (V25), remplace `locataireNom`/`locataireEmail` |
| `/api/rgpd/locataires/{id}` *(existant, sémantique étendue)* | DELETE | Effacement RGPD — cible désormais l'entité `Locataire` (tous ses baux) au lieu d'un `Bail` unique | BAILLEUR seul |

> Contrats détaillés (schémas req/resp) à figer en OpenAPI au début de l'implémentation, après
> approbation du Plan d'Exécution — cohérent avec la pratique déjà actée pour le reste du
> projet.

---

## 6. Matrice de traçabilité (addendum)

| Besoin (EB) | Exigence (CDC) | Règle métier | Cas de test prévu |
|-------------|------------------|---------------|---------------------|
| BF-97 | EF-97/98/99 | RM-100/101/102/107 | TC-98 cycle de vie Gestionnaire (suspension immédiate, archivage bloqué si affectation active ailleurs, restauration) ; TC-99 cross-tenant (affectation active chez un autre bailleur bloque l'archivage) |
| BF-98 | EF-100/101 | RM-103/104/105/106 | TC-100 CRUD Locataire + cloisonnement RLS ; TC-101 rejet bail sur Locataire archivé (409) ; TC-102 plusieurs baux successifs pour le même Locataire |
| BF-99 | EF-102 | — | TC-103 recherche multicritère (nom/téléphone/email/pièce d'identité) |
| BF-100 | EF-103 | — | TC-104 avertissement de doublon, création malgré tout confirmée explicitement |
| BF-101 | EF-104/105 | — | TC-105 écrans historique Gestionnaire/Locataire, aucune fuite de relations avec des tiers |
| ADR-16 D6 | EF-106 | — | TC-106 non-régression export/effacement RGPD, anonymisation multi-bail en une opération |
| ADR-16 D7 | EF-107 | — | TC-107 entrées `audit_log` sur les nouvelles opérations, aucune régression des points d'audit existants |

---

## 7. Score de maturité de l'addendum (/20)

| Axe | Note (0–4) | Commentaire |
|-----|-----------|-------------|
| Complétude | 3 | Un point de kickoff (K1, sémantique « créer » Gestionnaire) reste ouvert, à trancher avant Sprint 1 |
| Qualité | 4 | Critères ED/Q/A testables sur chaque EF |
| Sécurité | 3 | Fonction `SECURITY DEFINER` cross-tenant conçue (ADR-16 D4) mais non encore codée ni testée ; risque cross-bailleur du statut global explicitement accepté et tracé (RSV-EP15-01) |
| Traçabilité | 4 | Matrice BF→EF→RM→TC complète, numérotation sans collision (EF-97+, RM-100+) |
| Automatisation | 0 | Aucun code, aucune migration — conforme à la contrainte documentaire de ce cadrage |
| **Total** | **14/20** | « Solide » — ne constitue pas un Gate ; qualifie la maturité documentaire avant Plan d'Exécution. Le point K1 et RSV-EP15-01 sont les seuls éléments non définitivement clos avant Sprint 1 |
