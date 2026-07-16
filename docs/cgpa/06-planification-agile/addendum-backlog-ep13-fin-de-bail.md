# Addendum Backlog — Epic EP-13 (Fin de bail)

| Champ | Valeur |
|-------|--------|
| Document de référence | `product-backlog.md`, `addendum-backlog-ep10-ep12.md`, `addendum-backlog-ep15-personnes.md` — **non modifiés** |
| Statut | **Proposé** — cadrage documentaire ; kickoff K1→K6 (ADR-17) **entièrement ouvert**, aucun GO possible avant tranchage PO |
| Date | 2026-07-16 |
| Décision liée | ADR-17-fin-de-bail.md |
| Plan d'exécution | `plan-execution-ep13-fin-de-bail.md` (proposé, non approuvé) |

> **Numérotation.** US-01→114 sont déjà occupées (EP-01→15 ; EP-15 Sprint C non démarré). Ce
> document introduit **US-115 à US-118** sous l'epic **EP-13**, identifiant réservé depuis le
> cadrage EP-10→13 du 2026-07-01 (`analyse-impact-evolutions-ep10-ep13.md` §4, Évolution 7),
> sans modifier aucun backlog déjà validé.

---

## EP-13 — Fin de bail

| ID | Epic | Jalons | Priorité |
|----|------|--------|----------|
| EP-13 | **Fin de bail** — clôture effective d'un `Bail` (`ACTIF → CLOS`), aujourd'hui absente du produit malgré un statut `CLOS` déjà présent au schéma | Non planifié — cadrage seul à ce stade | Must |

### US-115 — Clôturer un bail actif

**En tant que** bailleur, **je veux** clôturer un bail actif **afin de** libérer le bien pour un
nouveau locataire, le statut `CLOS` étant aujourd'hui défini mais jamais assigné.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un `Bail` `ACTIF` de mon périmètre **W** je le clôture (date de clôture effective optionnelle, défaut aujourd'hui) **T** `statut → CLOS`, `dateClotureEffective` renseignée, `dateFin` (contractuelle) inchangée. **G** une `Garantie` non `RESTITUE_TOTAL` et/ou des paiements `IMPAYE`/`EN_RETARD` **W** je clôture malgré cela **T** la clôture réussit (200) avec la liste des avertissements dans la réponse — **aucun blocage** (proposition K3/K4, à confirmer par le PO). |
| Dépendances | Aucune (indépendante d'EP-15) |
| Priorité | Must |
| Points | 8 |
| Risques | RSV-EP13-01 (clôture prononcée sans garantie restituée ni impayés soldés, K3/K4 non bloquants) |
| Source | ADR-17 K1/K2/K3/K4 ; EF-108/109 |

### US-116 — Rouvrir un bail clos par erreur

**En tant que** bailleur, **je veux** rouvrir un bail clos par erreur **afin de** corriger une
clôture accidentelle sans perdre l'historique du bail.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un `Bail` `CLOS` **W** je le rouvre **T** `statut → ACTIF`, `dateClotureEffective` remise à `null`, **sauf** si un autre bail `ACTIF` existe déjà sur le même bien, auquel cas rejet 409 (`uq_bail_actif`, EF-12 inchangé). |
| Dépendances | US-115 |
| Priorité | Should |
| Points | 3 |
| Risques | RSV-EP13-03 (collision avec un bail créé entre-temps sur le même bien — mitigée par construction, contrainte déjà en base) |
| Source | ADR-17 K5 ; EF-110 |

### US-117 — Purger l'échéancier futur non exigible à la clôture

**En tant que** bailleur, **je veux** que les échéances de loyer futures déjà générées par le
batch mensuel disparaissent à la clôture **afin de** ne pas voir apparaître de fausses alertes
d'impayé sur un bail clos.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un `Bail` en cours de clôture avec des paiements `A_VENIR` de période postérieure à `dateClotureEffective` **W** la clôture s'exécute **T** ces paiements sont supprimés ; tout paiement déjà `RECU`/`PARTIEL`/`EN_RETARD`/`IMPAYE` reste intact (fait historique immuable, jamais retouché). |
| Dépendances | US-115 |
| Priorité | Should |
| Points | 5 |
| Risques | RSV-EP13-02 (suppression d'une échéance que le bailleur souhaitait conserver en trace, K6 à confirmer) |
| Source | ADR-17 K6 ; EF-111 |

### US-118 — Non-régression des alertes de pilotage sur un bail clos

**En tant que** bailleur, **je veux** qu'un bail clos n'émette plus jamais de nouvelle alerte de
pilotage **afin de** ne pas être sollicité pour un bail qui n'est plus en cours.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un `Bail` `CLOS` **W** le batch quotidien `generer_alertes` (US-50/51) s'exécute **T** aucune alerte `FIN_BAIL`/`PREAVIS`/`LOYER_EN_RETARD` n'est générée pour ce bail (le filtre `statut = 'ACTIF'` déjà présent pour `FIN_BAIL`/`PREAVIS` est vérifié/étendu si nécessaire pour couvrir `LOYER_EN_RETARD` compte tenu d'US-117). |
| Dépendances | US-115, US-117 |
| Priorité | Must |
| Points | 3 |
| Risques | Aucun nouveau — non-régression pure |
| Source | Non-régression EF-60/61/63 ; EF-112 |

---

## Récapitulatif & priorisation

| Story | Points | Priorité |
|-------|--------|----------|
| US-115 — Clôturer un bail actif | 8 | Must |
| US-116 — Rouvrir un bail clos | 3 | Should |
| US-117 — Purger l'échéancier futur | 5 | Should |
| US-118 — Non-régression alertes | 3 | Must |
| **Total EP-13** | **19** | — |

## Dépendances & risques (synthèse)

- **K1→K6 (ADR-17)** : **aucun tranché** à ce stade — contrairement à EP-15 qui n'avait qu'un
  point ouvert (K1) au même stade de cadrage. Aucun GO explicite du PO sur un Plan d'Exécution
  n'est possible avant ce tranchage.
- **RSV-EP13-01** (garantie/impayés non soldés à la clôture, non bloquant par proposition) :
  dépend directement de K3/K4 — pourrait devenir un blocage dur si le PO en décide autrement.
- **RSV-EP13-02** (purge d'échéances futures, K6) : dépend du tranchage K6 — pourrait être
  retirée du périmètre si le PO préfère conserver ces échéances.
- **RSV-EP13-03** (collision de réouverture) : mitigée par construction (contrainte déjà en
  base), aucune action supplémentaire requise.
- **RSV-EP13-04** (coordination de séquencement avec le Sprint C d'EP-15, aucune dépendance
  technique directe) : point de coordination Release Management, non bloquant pour le codage.
- Aucun sprint ne peut démarrer avant un GO explicite du PO sur `plan-execution-ep13-fin-de-bail.md`,
  lui-même conditionné au tranchage préalable de K1→K6.
