# Gate 07A — Release Readiness · Dossier de décision (LoyerTracker)

> Gate ajouté par **CGPA v5.2** (`setup-cgpa/docs/cgpa/gates/gate-07A-release.md`,
> `release-governance.md`). Vérifie qu'une release est identifiable, documentée, déployable et
> réversible avant promotion. **Statut : GO SOUS RÉSERVE** — ratifié le **2026-06-19** après la
> validation staging de l'alerting (OBS-02/03) et la re-validation du **Gate Staging enrichi (GO)**.
> Traite la réserve **R-V52-5** (volet release). Réserve résiduelle **RR-2** portée au go-live réel.

## Version & cible

- **Version release** : `1.0.0` (Semantic Versioning).
- **Environnement cible** : **Production** (préparée) — **validée sur Staging** (déploiement enrichi
  alerting prouvé le 2026-06-19, cf. `staging-state.md` §10) ; go-live réel différé.
- **Artefact** : image GHCR `loyertracker-{api,web}` au tag immuable `sha-<8>` (produit au merge sur `main`).

## Conditions d'entrée

- [x] Version cible définie — `1.0.0`.
- [x] Périmètre de release clarifié — `CHANGELOG.md` [1.0.0], `docs/release-notes-v1.0.0.md`.
- [x] Artefact / commit identifié — image GHCR par tag immuable.
- [x] Environnement cible identifié — Production (validée sur Staging).
- [x] Rollback pressenti — redéploiement du tag immuable précédent.

## Critères GO

| # | Critère GO | État | Preuve |
|---|------------|------|--------|
| 1 | Version identifiée (SemVer) | ✅ | `1.0.0` (`pom.xml`, `package.json`) |
| 2 | Changelog produit | ✅ | `CHANGELOG.md` [1.0.0] |
| 3 | Release notes produites | ✅ | `docs/release-notes-v1.0.0.md` |
| 4 | Historique des décisions à jour | ✅ | `docs/project-state.md` §11 |
| 5 | Rollback documenté | ✅ | runbook §3, `staging-state.md` §7, `infra/backup/` |
| 6 | Déploiement validé sur l'environnement applicable | ✅ | **Gate Staging enrichi GO le 2026-06-19** ; alerting prouvé par simulation d'incident (4/4 composants critiques FIRING→notifié→resolved, smoke 46/0) — `staging-state.md` §10 |
| 7 | Traçabilité production prête (si cible Production) | 🟡 | gabarit prêt (release notes §5) ; renseigné au go-live réel (différé — RR-2) |

## Réserves / actions correctives (datées)

| # | Réserve | Échéance | Statut |
|---|---------|----------|--------|
| RR-1 | Validation staging par simulation d'incident de l'alerting (OBS-02/03) + re-validation du **Gate Staging enrichi** (logs / monitoring / alertes critiques définies) | Avant ratification Gate 07A | ✅ **Levée le 2026-06-19** (`staging-state.md` §10) |
| RR-2 | Renseigner la traçabilité production (release notes §5) au go-live réel | Au go-live (lot ultérieur, Gate 09/10) | ⏳ Ouverte |

## Sous-agent mobilisé

| Sous-agent | Avis |
|------------|------|
| Release Manager | **GO sous réserve** : version SemVer, changelog, release notes et rollback prêts ; **RR-1 levée** (déploiement enrichi alerting prouvé sur staging, `staging-state.md` §10). Reste RR-2 (traçabilité production), légitimement portée au go-live réel. |

## Décision

- **Statut : GO SOUS RÉSERVE** — ratifié le **2026-06-19** par le CGPA Chief Delivery Officer.
- **6/7 critères satisfaits** (version, changelog, release notes, historique, rollback, **déploiement
  enrichi validé**) ; le 7ᵉ (traçabilité production) est porté par **RR-2**, renseigné au go-live réel.
- **Réserve résiduelle RR-2** : remplir les release notes §5 (date, tag, opérateur, vérifications) lors
  de la promotion production effective (Gate 09 — Production Readiness / Gate 10 — Mise en production).
- La release `1.0.0` est **autorisée à la promotion production** sous réserve de la levée de RR-2 au
  go-live et du franchissement des Gates 09/10 (hors périmètre de ce gate).

> ✅ **Statué GO sous réserve.** Les 5 critères documentaires + le critère de déploiement enrichi (#6)
> sont satisfaits ; seule la traçabilité production (#7, RR-2) reste à renseigner au go-live réel.
> Décision consignée ici et dans `docs/project-state.md` (§3, §3B, §11, §13).

---
*Livrable CGPA v5.2 — Gate 07A (Release Readiness). Réf. : `setup-cgpa/docs/cgpa/gates/gate-07A-release.md`,
`setup-cgpa/docs/cgpa/release-governance.md`.*
