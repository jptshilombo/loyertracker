# Gate 07A — Release Readiness · Dossier de décision (LoyerTracker)

> Gate ajouté par **CGPA v5.2** (`setup-cgpa/docs/cgpa/gates/gate-07A-release.md`,
> `release-governance.md`). Vérifie qu'une release est identifiable, documentée, déployable et
> réversible avant promotion. **Statut : NON STATUÉ** — dossier préparé (cap production PR 2),
> décision différée jusqu'à la validation staging de l'alerting (OBS-02/03) et la re-validation
> du Gate Staging enrichi. Traite la réserve **R-V52-5** (volet release).

## Version & cible

- **Version release** : `1.0.0` (Semantic Versioning).
- **Environnement cible** : **Production** (préparée) — validée sur **Staging** ; go-live réel différé.
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
| 6 | Déploiement validé sur l'environnement applicable | 🟡 | staging GO (v4.0) ; **re-validation enrichie (alerting) à rejouer** par simulation d'incident |
| 7 | Traçabilité production prête (si cible Production) | 🟡 | gabarit prêt (release notes §5) ; renseigné au go-live réel (différé) |

## Réserves / actions correctives (datées)

| # | Réserve | Échéance |
|---|---------|----------|
| RR-1 | Validation staging par simulation d'incident de l'alerting (OBS-02/03) + re-validation du **Gate Staging enrichi** (logs / monitoring / alertes critiques définies) | Avant ratification Gate 07A |
| RR-2 | Renseigner la traçabilité production (release notes §5) au go-live réel | Au go-live (lot ultérieur, Gate 09/10) |

## Sous-agent mobilisé

| Sous-agent | Avis (préliminaire) |
|------------|---------------------|
| Release Manager | **GO sous réserve** dès RR-1 levée : version SemVer, changelog, release notes et rollback prêts ; ne manque que la preuve de déploiement enrichi (alerting) sur staging. |

## Décision

- **Statut : NON STATUÉ** (dossier préparé en parallèle, cap production PR 2).
- Décision attendue : **GO sous réserve** une fois **RR-1** levée (validation staging de l'alerting +
  Gate Staging enrichi) ; **NO GO** si le déploiement enrichi n'est pas validé.
- Ratification : à prononcer par le CGPA Chief Delivery Officer après la validation staging.

> ⏳ **À statuer.** Les 5 critères documentaires (version, changelog, release notes, historique,
> rollback) sont satisfaits ; restent les critères de déploiement validé (#6) et de traçabilité
> production (#7), portés par RR-1/RR-2. La décision GO sera consignée ici et dans
> `docs/project-state.md` dès la validation staging.

---
*Livrable CGPA v5.2 — Gate 07A (Release Readiness). Réf. : `setup-cgpa/docs/cgpa/gates/gate-07A-release.md`,
`setup-cgpa/docs/cgpa/release-governance.md`.*
