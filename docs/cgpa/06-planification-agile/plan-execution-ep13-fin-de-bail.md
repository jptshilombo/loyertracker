# Plan d'Exécution CGPA — EP-13 : Fin de bail

| Champ | Valeur |
|---|---|
| Date | 2026-07-16 |
| Statut | **Approuvé — GO explicite du PO le 2026-07-16.** Kickoff K1→K6 (ADR-17) clos ; Sprint unique autorisé à démarrer |
| Origine | Instruction PO du 2026-07-16 (« cadrer EP-13 fin de bail ») ; identifiant réservé depuis le cadrage EP-10→13 du 2026-07-01 |
| Backlog couvert | EP-13 — US-115 → US-118 (`addendum-backlog-ep13-fin-de-bail.md`) |
| ADR | **ADR-17** (Proposée — kickoff K1→K6 clos, 2026-07-16) |
| Release cible | À déterminer après GO — indépendant du Sprint C d'EP-15 (aucune dépendance technique directe, cf. RSV-EP13-04) ; coordination de séquencement Production à arbitrer si les deux lots sont conduits en parallèle |
| Prérequis | Release `1.10.0` clôturée (CDO GO) — **atteint le 2026-07-16** (`docs/cgpa/09-production/cloture-release-v1.10.0.md`) |

## Kickoff — clos le 2026-07-16 (PO)

| # | Question | Décision | Statut |
|---|---|---|---|
| K1 | Déclenchement de la clôture : manuel uniquement, jamais de batch automatique sur `dateFin` dépassée ? | Oui | ✅ Tranché |
| K2 | Ajouter `bail.date_cloture_effective`, distincte de `dateFin` (contractuelle) ? | Oui | ✅ Tranché |
| K3 | Garantie non totalement restituée à la clôture : avertissement ou blocage (409) ? | Avertissement | ✅ Tranché — confirmé malgré l'attention particulière demandée (obligation légale/financière envers le locataire sortant) |
| K4 | Impayés/loyers en retard à la clôture : avertissement ou blocage (409) ? | Avertissement | ✅ Tranché |
| K5 | Réouverture d'un bail clos par erreur autorisée (sous réserve `uq_bail_actif`) ? | Oui | ✅ Tranché |
| K6 | Purge des échéances `A_VENIR` futures au-delà de la clôture ? | Oui | ✅ Tranché |

**Le kickoff K1→K6 est clos — les six propositions par défaut de l'ADR-17 ont été validées sans
modification.** Seul un **GO explicite du PO sur ce Plan d'Exécution** reste requis avant tout
démarrage de sprint.

## Vue d'ensemble

Contrairement à EP-15 (trois sprints séquencés par une contrainte de migration non additive),
EP-13 est un lot **entièrement additif** (un seul champ nouveau, aucune suppression de colonne,
aucun rollback applicatif complexe) — un **sprint unique** suffit à couvrir tout le périmètre.

```
Sprint unique — Fin de bail
Clôture (US-115) → Réouverture (US-116) → Purge échéancier (US-117) → Non-régression alertes (US-118)
```

## Sprint unique — Clôture effective d'un bail

| Champ | Valeur |
|---|---|
| Objectif | Un bail actif peut être clôturé (libérant le bien pour un nouveau bail), rouvert en cas d'erreur, sans coupler techniquement ce cycle de vie à la Garantie ou aux Paiements |
| Stories | **US-115** (clôture), **US-116** (réouverture), **US-117** (purge échéancier futur), **US-118** (non-régression alertes) |
| Livrables | Migration additive (numéro réservé au démarrage du sprint, après vérification d'absence de collision avec toute migration alors en cours) : `bail.date_cloture_effective DATE` nullable ; `BailService.cloturer()`/`rouvrir()` ; nouveaux endpoints `POST .../cloture` et `POST .../reouverture` ; extension `BailDto` ; ajustement du filtre du batch d'alertes (`generer_alertes`) si nécessaire pour `LOYER_EN_RETARD` ; tests unitaires + intégration (clôture avec avertissements, réouverture avec/sans collision, purge d'échéancier, non-régression alertes) |
| Hors périmètre | Toute orchestration transactionnelle Garantie+Paiements+Bail (`ClotureBailService` anticipé par ADR-14, explicitement écarté par ce cadrage) ; toute modification du module Garantie ou Paiements eux-mêmes ; toute interaction avec le Sprint C d'EP-15 (`Bail.locataireId`) |
| Dépendances | Kickoff K1→K6 tranché par le PO ; GO explicite du PO sur ce Plan d'Exécution |
| Risques | RSV-EP13-01 (avertissement non bloquant sur garantie/impayés, dépend de K3/K4) ; RSV-EP13-02 (purge d'échéancier, dépend de K6) ; RSV-EP13-03 (collision de réouverture, mitigée par construction) ; RSV-EP13-04 (coordination de séquencement avec Sprint C EP-15, non bloquant) |
| Critères GO (fin de sprint) | ✅ `mvn verify`/`ng test` verts sans régression ✅ clôture/réouverture testées avec et sans avertissements/collision ✅ purge d'échéancier ne touchant jamais un paiement déjà survenu (test dédié) ✅ aucune alerte générée pour un bail `CLOS` (test dédié) ✅ migration additive, rollback applicatif viable ✅ CI complète verte ✅ Gate Staging (dont `STG-ISOL-01` sur l'hôte mutualisé) → Gate Production distinct |

## Gouvernance transverse

| Artefact | Échéance |
|---|---|
| ADR-17 proposée (K1→K6 à trancher) | ✅ 2026-07-16 |
| Addendum EB (BF-102→105) | Produit avec ce plan |
| Addendum CDC (EF-108→112, RM-108→113, ENF-93) | Produit avec ce plan |
| Addendum backlog EP-13 (US-115→118) | Produit avec ce plan |
| Kickoff K1→K6 tranché par le PO | À faire avant tout GO |
| `CHANGELOG.md` `[Non publié]` au fil du sprint | À la fusion `main` |
| `docs/project-state.md` / `staging-state.md` / `prod-state.md` | Chaque Gate |

## Checklist de validation CGPA (avant tout codage)

- [x] `docs/project-state.md` lu, phase courante identifiée (CGPA v5.4.1, release `1.10.0` clôturée le 2026-07-16 — CDO GO)
- [x] Aucune décision, Gate ou risque historique supprimé ou réécrit
- [x] Numérotation vérifiée sans collision (EP-13, US-115→118, ADR-17, EF-108→112, RM-108→113, ENF-93)
- [x] Impact Staging/Production/Release Management analysé (aucun déploiement à ce stade)
- [x] Kickoff K1→K6 tranché par le PO (2026-07-16)
- [x] Plan d'Exécution approuvé (GO explicite du PO, 2026-07-16)
- [ ] Sprint instruit avec son propre Gate Staging (dont `STG-ISOL-01`) et sa propre décision Gate Production

## Ce que ce plan autorise désormais

- Le codage du Sprint unique (US-115→118), dans le périmètre exact défini ci-dessus.
- Aucun développement de `ClotureBailService` orchestrant Garantie+Paiements+Bail en une seule
  transaction — explicitement hors périmètre (ADR-17 §Alternatives écartées), GO ne couvre pas
  cette extension.
- Aucune modification du module Garantie ou Paiements eux-mêmes.
- Aucune promotion Staging/Production sans son Gate (dont `STG-ISOL-01`) — le GO sur le Plan
  d'Exécution n'est pas un GO sur un déploiement.
- Aucune modification des décisions historiques (ADR-01/EF-12 restent vrais pour la contrainte
  d'unicité `uq_bail_actif`, réutilisée telle quelle par la réouverture).
