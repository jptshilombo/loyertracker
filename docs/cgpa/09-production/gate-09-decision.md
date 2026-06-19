# Gate 09 — Production Readiness · Dossier de décision (LoyerTracker)

> Gate CGPA (`setup-cgpa/docs/cgpa/gates/gate-09-production-readiness.md`, phase 09). Confirme que la
> solution, l'organisation et l'exploitation sont prêtes pour la production avant la mise en production
> (Gate 10). **Statut : GO SOUS RÉSERVE** — ratifié le **2026-06-19**.
> S'appuie sur la **Production Readiness Review** du même jour (`production-readiness-review.md`).

## Version & cible

- **Release** : `1.0.0` (SemVer, Release-Ready) — image GHCR au tag immuable `sha-<8>`.
- **Environnement cible** : **Production sur hôte dédié** (distinct de staging, ENV-01 strict) — stack
  `loyertracker-prod`, domaine `loyertracker.loyerpro.org`. **Non encore provisionné** : la mise en
  production effective relève du **Gate 10**.
- **Recette** : **waiver tracé** (cf. critère #2) — smoke 46/0 + Gate Staging enrichi GO en équivalence.

## Conditions d'entrée

- [x] Phase 09 exécutée (production readiness review produite — `production-readiness-review.md`).
- [x] Livrables versionnés et accessibles (`docs/`, `infra/`, `docker-compose.prod.yml`).
- [x] Risques, hypothèses et décisions listés (`docs/project-state.md` §11/§13).
- [x] Score de maturité calculé (review : 19/24 ≈ 3,2/4, niveau Solide).

## Critères GO

| # | Critère | État | Preuve |
|---|---------|------|--------|
| 1 | Release identifiable, documentée, déployable, réversible | ✅ | Gate 07A GO sous réserve ; `docker-compose.prod.yml` aligné GHCR/tag immuable ; rollback par tag immuable |
| 2 | QA / recette finalisée (ou équivalence tracée) | ✅ (waiver) | **Waiver** : smoke `infra/smoke/smoke-stack.sh` **46/0** + **Gate Staging enrichi GO** (`staging-state.md` §10) ; pas de Gate 08 formel (arbitrage PO 2026-06-19) |
| 3 | Monitoring opérationnel | ✅ | Prometheus 4/4 cibles `up` live (RR-1) ; scrape interne 200 / public 404 ; overlay réutilisable |
| 4 | Alerting des composants critiques prouvé | ✅ | RR-1 : 4/4 composants FIRING→notifié→resolved, notification bout-en-bout (`staging-state.md` §10) |
| 5 | Rollback documenté et prouvé | ✅ | Tag immuable précédent ; drill de restauration réel RPO 24 h / RTO < 1 h (PR #26) |
| 6 | Runbooks d'exploitation disponibles | ✅ | `docs/cgpa/07-devsecops/runbook-exploitation.md` (éprouvé sur staging) |
| 7 | DevSecOps / sécurité vérifiés | ✅ | **Gate 06A GO** (DSO-01→05 automatisés) ; secrets hors dépôt ; Trivy bloquant ; monitoring non publié |
| 8 | Capacity évaluée | 🟡 | Pas de campagne de charge — **RG-09-1** (acceptée PME, surveillée par monitoring) |
| 9 | Modèle de support / astreinte | 🟡 | Support informel (opérateur unique PME) — **RG-09-2** (processus minimal à documenter au Gate 10) |
| 10 | Traçabilité production prête | 🟡 | Gabarit prêt (release-notes §5) ; renseigné au go-live — **RR-2** |

## Réserves / actions correctives (datées)

| # | Réserve | Responsable | Échéance | Statut |
|---|---------|-------------|----------|--------|
| RG-09-1 | Capacity : définir une baseline de sizing de l'hôte dédié + surveiller la tenue via le monitoring | PO / Exploitation | Au provisioning hôte (Gate 10) puis revue en exploitation | ⏳ Ouverte |
| RG-09-2 | Support : documenter un processus de support/incident minimal (canal d'astreinte, point de contact) | PO | Avant / au Gate 10 | ⏳ Ouverte |
| RR-2 | Traçabilité production (release-notes §5 : date, tag, opérateur, vérifications) | Release Manager | Au déploiement effectif (Gate 10) | ⏳ Ouverte (héritée Gate 07A) |

## Sous-agents mobilisés

| Sous-agent | Avis |
|------------|------|
| Governance Officer | **GO sous réserve** : continuité respectée, décisions traçables, waiver recette explicitement consigné (pas de gate masqué). Aucune dette de gouvernance critique. |
| Enterprise Architect | **GO sous réserve** : cible Production sur **hôte dédié** distinct de staging conforme à ENV-01 (Staging ≠ Production) ; `docker-compose.prod.yml` aligné GHCR/tag immuable ; isolation à prouver sur l'hôte réel (Gate 10). |
| DevSecOps Lead | **GO** : Gate 06A GO, secrets hors dépôt, Trivy bloquant, monitoring/Pushgateway non publiés, alerting prouvé end-to-end (RR-1). Rien de bloquant côté sécurité/livraison. |
| Release Manager | **GO sous réserve** : release `1.0.0` identifiable/documentée/déployable/réversible ; restent RG-09-1, RG-09-2 (organisationnelles) et RR-2 (au go-live) — non bloquantes. |

## Décision

- **Statut : GO SOUS RÉSERVE** — ratifié le **2026-06-19** par le CGPA Chief Delivery Officer.
- **Score** : 19/24 ≈ **3,2/4 (Solide)**. Socle d'exploitation solide (monitoring, alerting, rollback,
  runbooks prouvés en conditions réelles sur staging) ; réserves concentrées sur la maturité
  organisationnelle (capacity, support), légitimes en contexte PME et **non bloquantes**.
- **La solution est déclarée prête à la mise en production.** L'ouverture du **Gate 10 — Mise en
  production** est autorisée, conditionnée à : provisioning de l'hôte dédié, levée de RR-2 au déploiement,
  et traitement de RG-09-1 / RG-09-2 selon leur échéance.

> ✅ **Statué GO sous réserve.** Décision consignée ici, dans `production-readiness-review.md` et dans
> `docs/project-state.md` (§3, §11, §12, §13).

---
*Livrable CGPA v5.2 — Gate 09 (Production Readiness). Réf. : `setup-cgpa/docs/cgpa/gates/gate-09-production-readiness.md`,
`setup-cgpa/docs/cgpa/phases/phase-09-production-readiness.md`.*
