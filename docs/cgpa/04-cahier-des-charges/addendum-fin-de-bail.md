# Addendum CDC — Fin de bail (clôture effective d'un `Bail`)

| Champ | Valeur |
|-------|--------|
| Document de référence | `cahier-des-charges.md` (✅ Validé — Gate 3 Go, 2026-06-04) — **non modifié** |
| Document de référence | `dossier-architecture.md` (✅ Validé — Gate 4 Go, 2026-06-04) — étendu §3.5 (additif) |
| Statut de l'addendum | **Proposé** — cadrage documentaire ; aucun codage ni migration SQL engagé. Kickoff K1→K6 (ADR-17) entièrement ouvert |
| Date | 2026-07-16 |
| Décision liée | `ADR-17-fin-de-bail.md` |
| Base besoin | `docs/cgpa/02-expression-besoin/addendum-fin-de-bail.md` (BF-102→BF-105) |

---

## 1. Exigences fonctionnelles détaillées (addendum)

### 1.1 Clôture d'un bail *(nouveau)*

| ID | Exigence | Critère d'acceptation | Priorité | Source |
|----|----------|------------------------|----------|--------|
| EF-108 | Clôture manuelle d'un bail actif | ED un bailleur, un `Bail` `ACTIF` de son périmètre · Q il déclenche la clôture (date de clôture effective en paramètre, par défaut aujourd'hui) · A `statut → CLOS`, `date_cloture_effective` renseignée, `dateFin` (contractuelle) inchangée ; aucun automatisme ne clôture un bail sans cette action explicite (ADR-17 K1). | Must | BF-102 |
| EF-109 | Avertissements non bloquants à la clôture | ED un `Bail` dont la `Garantie` n'est pas `RESTITUE_TOTAL` et/ou dont des paiements sont `IMPAYE`/`EN_RETARD`/`PARTIEL` · Q le bailleur le clôture · A la clôture **réussit** (200), la réponse contient la liste des avertissements (garantie non soldée, paiements en souffrance) ; aucun blocage 409. **Proposition à confirmer (K3/K4)** — une alternative bloquante (409) reste possible si le PO en décide autrement au kickoff. | Must | BF-104 |
| EF-110 | Réouverture d'un bail clos | ED un `Bail` `CLOS` · Q le bailleur le rouvre · A `statut → ACTIF` si et seulement si aucun autre `Bail` `ACTIF` n'existe déjà sur le même bien (`uq_bail_actif` inchangé, EF-12) ; sinon rejet 409. `date_cloture_effective` est remise à `null`. | Should | BF-103 |
| EF-111 | Purge de l'échéancier futur non exigible | ED un `Bail` en cours de clôture, avec des paiements générés par le batch mensuel (US-30) au statut `A_VENIR` et de période strictement postérieure à `date_cloture_effective` · Q la clôture s'exécute · A ces paiements sont supprimés ; tout paiement déjà `RECU`/`PARTIEL`/`EN_RETARD`/`IMPAYE` est conservé intact (fait historique immuable). **Proposition à confirmer (K6)**. | Should | BF-105 |
| EF-112 | Non-régression de la génération d'alertes | ED un `Bail` `CLOS` · Q le batch quotidien d'alertes (`generer_alertes`, US-50/51) s'exécute · A aucune alerte `FIN_BAIL`/`PREAVIS`/`LOYER_EN_RETARD` n'est générée pour un bail `CLOS` (le batch existant filtre déjà sur `statut = 'ACTIF'` pour `FIN_BAIL`/`PREAVIS` — à vérifier/étendre si nécessaire pour `LOYER_EN_RETARD` compte tenu d'EF-111). | Must | Non-régression EF-60/61/63 |

---

## 2. Exigences non fonctionnelles (addendum)

| ID | Catégorie | Exigence | Critère d'acceptation | Source |
|----|-----------|----------|-----------------------|--------|
| ENF-93 | Intégrité — Réouverture sans collision | La réouverture d'un bail (EF-110) ne doit jamais produire deux baux `ACTIF` simultanés sur le même bien, y compris en cas de requêtes concurrentes. | ED deux requêtes de réouverture/création concurrentes sur le même bien · Q elles s'exécutent en parallèle · A la contrainte unique `uq_bail_actif` (déjà en base) fait échouer l'une des deux transactions (409), aucune violation silencieuse. | ADR-17 K5 |

---

## 3. Registre des règles métier (RM) — *complète le registre ouvert par RM-100→107 (EP-15)*

| ID | Règle métier | Exigence(s) liée(s) |
|----|--------------|----------------------|
| RM-108 | La clôture d'un bail est exclusivement une action bailleur explicite ; aucun batch ne la déclenche automatiquement. | EF-108 |
| RM-109 | `date_cloture_effective` est distincte de `dateFin` (contractuelle) et n'est renseignée qu'à la clôture. | EF-108 |
| RM-110 | La clôture n'est jamais bloquée par l'état de la garantie ou des paiements — avertissement uniquement (proposition, cf. ADR-17 K3/K4). | EF-109 |
| RM-111 | Un bail ne peut être `ACTIF` que si aucun autre bail `ACTIF` n'existe sur le même bien (= EF-12/`uq_bail_actif`, déjà couvert, réutilisé pour la réouverture). | EF-110 |
| RM-112 | Un paiement déjà survenu (`RECU`/`PARTIEL`/`EN_RETARD`/`IMPAYE`) n'est jamais supprimé, quelle que soit l'opération de clôture. | EF-111 |
| RM-113 | Un bail `CLOS` ne génère plus de nouvelle alerte de pilotage (`FIN_BAIL`, `PREAVIS`, `LOYER_EN_RETARD`). | EF-112 |

---

## 4. Modèle de données (proposition logique)

### 4.1 Diagramme logique mis à jour

```
Bail (statut ACTIF|CLOS)
  ├─ dateFin              (contractuelle, existante, inchangée)
  └─ dateClotureEffective (nouvelle, nullable, renseignée uniquement à CLOS)
```

> Aucune nouvelle entité — extension du `Bail` existant uniquement. `Garantie`/
> `GarantieMovement` (ADR-14) et `Paiement` (EF-30) restent inchangés et non couplés
> techniquement à ce cycle de vie (ADR-17 K3/K4).

### 4.2 Nouvel attribut (proposition)

| Entité | Nouveauté | Détail |
|--------|-----------|--------|
| `Bail` | `dateClotureEffective` *(nouveau, nullable)* | `LocalDate`, `NULL` tant que `statut = ACTIF` ; renseignée à la clôture (EF-108) ; remise à `null` à la réouverture (EF-110) |

### 4.3 Cardinalités & contraintes d'intégrité (conceptuelles)

| Règle | Contrainte conceptuelle |
|-------|--------------------------|
| RM-111 (= EF-12 existant) | Inchangé : `CREATE UNIQUE INDEX uq_bail_actif ON bail (bien_id) WHERE statut = 'ACTIF';` — la réouverture réutilise cette contrainte sans modification |
| RM-108 | Aucune contrainte SQL déclarative — la clôture est un changement d'état applicatif, comme l'archivage `Bien`/`Patrimoine`/`Gestionnaire`/`Locataire` |
| EF-111 | Suppression ciblée `DELETE FROM paiement WHERE bail_id = ? AND statut = 'A_VENIR' AND periode > ?` — jamais sur un autre statut |

### 4.4 Index de performance (proposition)

Aucun nouvel index requis : la suppression ciblée (EF-111) s'appuie sur l'index existant
`paiement(bail_id, periode)` (US-30/US-31, déjà en place).

### 4.5 Impact migration base de données *(narratif)*

Une seule migration additive proposée (numéro à réserver au Plan d'Exécution, après confirmation
qu'aucune collision n'existe avec une migration en cours à cette date) : ajout de
`bail.date_cloture_effective DATE` (nullable). Rollback applicatif simple (colonne additive
uniquement, aucune suppression), contrairement à V25/Sprint C EP-15.

---

## 5. Contrats d'API impactés (proposition — non implémentée)

| Endpoint (proposé) | Méthode | Description | Sécurité |
|---------------------|---------|--------------|----------|
| `/api/biens/{bienId}/baux/{bailId}/cloture` | POST | Clôturer un bail actif (`dateClotureEffective` optionnelle, défaut = aujourd'hui) ; renvoie les avertissements éventuels (garantie/paiements) | BAILLEUR (propriétaire du bien) |
| `/api/biens/{bienId}/baux/{bailId}/reouverture` | POST | Rouvrir un bail clos (409 si un bail actif existe déjà sur le bien) | BAILLEUR (propriétaire du bien) |
| `/api/biens/{bienId}/baux/{bailId}` *(existant, étendu)* | GET | Réponse `BailDto` étendue avec `dateClotureEffective` (nullable) | Inchangée |

> Contrats détaillés (schémas req/resp, structure exacte des avertissements EF-109) à figer en
> OpenAPI au début de l'implémentation, après approbation du Plan d'Exécution — cohérent avec la
> pratique déjà actée pour le reste du projet.

---

## 6. Matrice de traçabilité (addendum)

| Besoin (EB) | Exigence (CDC) | Règle métier | Cas de test prévu |
|-------------|------------------|---------------|---------------------|
| BF-102 | EF-108 | RM-108/109 | TC-108 clôture d'un bail actif, `dateClotureEffective` renseignée, `dateFin` inchangée |
| BF-104 | EF-109 | RM-110 | TC-109 clôture réussie malgré garantie non restituée/impayés, avertissements présents dans la réponse |
| BF-103 | EF-110 | RM-111 | TC-110 réouverture réussie ; TC-111 réouverture rejetée (409) si un bail actif existe déjà sur le bien |
| BF-105 | EF-111 | RM-112 | TC-112 purge des échéances `A_VENIR` futures ; TC-113 non-suppression des paiements déjà survenus |
| Non-régression EF-60/61/63 | EF-112 | RM-113 | TC-114 aucune alerte générée pour un bail `CLOS` après clôture |

---

## 7. Score de maturité de l'addendum (/20)

| Axe | Note (0–4) | Commentaire |
|-----|-----------|-------------|
| Complétude | 2 | Six points de kickoff (K1→K6) intégralement ouverts — aucun tranché à ce stade, contrairement à EP-15 qui n'en avait qu'un (K1) au même stade de cadrage |
| Qualité | 4 | Critères ED/Q/A testables sur chaque EF |
| Sécurité | 4 | Aucune surface cross-tenant nouvelle, RBAC identique à la création de bail existante (EF-12) |
| Traçabilité | 4 | Matrice BF→EF→RM→TC complète, numérotation sans collision (EF-108+, RM-108+, ENF-93) |
| Automatisation | 0 | Aucun code, aucune migration — conforme à la contrainte documentaire de ce cadrage |
| **Total** | **14/20** | « Solide » côté qualité/traçabilité/sécurité mais **complétude délibérément limitée** tant que K1→K6 ne sont pas tranchés par le PO — ne constitue pas un Gate ; qualifie la maturité documentaire avant tout Plan d'Exécution GO |
