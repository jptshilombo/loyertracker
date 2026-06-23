# Release Candidate — LoyerTracker `1.1.0-rc.1`

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-23 |
| Type | Release fonctionnelle candidate |
| Version candidate | `1.1.0-rc.1` |
| Version cible Production | `1.1.0` |
| Périmètre | Quittances de loyer + Patrimoine Sprint 1/2 |
| Base Production actuelle | `1.0.0` (`sha-73359c5c`) |
| Environnement source | Staging |
| Décision actuelle | **Déployée en Production le 2026-06-23 — `PRODUCTION_DEPLOYED`** |

## 1. Objet

Ce document prépare une Release candidate post-go-live incluant les lots `[Non publié]` déjà intégrés côté `main` :

- Quittances de loyer.
- Patrimoine Sprint 1.
- Patrimoine Sprint 2.
- Correctifs de validation Staging liés au lot Patrimoine.

Il ne vaut pas autorisation Production. La mise en Production nécessite un Gate Production explicite selon CGPA v5.3.

## 2. Périmètre fonctionnel candidat

### Quittances de loyer

- Ventilation du bail en loyer hors charges et provision de charges.
- Profil bailleur avec adresse postale.
- Génération PDF à la volée :
  - quittance de loyer pour loyer `RECU` ;
  - avis d'échéance pour loyer non soldé.
- Boutons frontend de téléchargement.
- Accès ReBAC bailleur / gestionnaire affecté.

### Patrimoine Sprint 1

- Nouveau niveau `Patrimoine` entre bailleur et bien.
- Typologie administrable `TypeBien`.
- Rattachement obligatoire des biens à un patrimoine.
- Migration V12 avec patrimoine par défaut.
- RLS et non-régression associées.

### Patrimoine Sprint 2

- Affectations au niveau patrimoine.
- Héritage dynamique ReBAC vers les biens du patrimoine.
- Liste effective gestionnaire sans doublons.
- Garde RS-06 : archivage d'un patrimoine avec affectation patrimoine `ACTIVE` refusé.
- Migration V13.

### Correctifs Staging associés

- Smoke Staging réaligné sur V13 + patrimoine.
- Honoraires patrimoine corrigés via migration V14.
- Compteur Flyway du smoke aligné V14 via PR #77.

## 3. Hors périmètre

- Sprint 3 Patrimoine.
- Logique complète `EXCLUSION`.
- UX avancée de périmètre effectif.
- Export/effacement RGPD.
- Refonte globale des dashboards.
- Tout changement Production avant Gate Production.

## 4. Preuves disponibles

| Domaine | État | Preuve |
|---------|------|--------|
| Quittances | Terminé et mergé | PR #70 + PR #71, changelog `[Non publié]` |
| Patrimoine Sprint 1 | Clôturé | PR #72 + PR #73, `sprint-1-patrimoine-cloture.md` |
| Patrimoine Sprint 2 | Clôturé | PR #74, `sprint-2-patrimoine-cloture.md` |
| Correctif honoraires patrimoine | Validé | PR #76, migration V14, CI verte |
| Smoke Staging | Validé | PR #77, 47 PASS / 0 FAIL |
| Gate Staging v5.3 | GO | `gate-staging-patrimoine-v5.3-decision.md`, statut `STAGING_DEPLOYED` |
| Production actuelle | Stable | `1.0.0` LIVE, Gate 10 GO |

## 5. Analyse Release Management

| Critère CGPA v5.3 | Statut | Commentaire |
|-------------------|--------|-------------|
| Périmètre Production identifié | OK | Release fonctionnelle `1.1.0-rc.1` |
| Version SemVer identifiée | OK | Candidate `1.1.0-rc.1`, cible `1.1.0` |
| Preuves Staging disponibles | OK | Lot Patrimoine `STAGING_DEPLOYED`, smoke 47/0 |
| Changelog disponible | OK | Section `[Non publié]` à convertir en `1.1.0` si Gate Production GO |
| Release notes disponibles | OK sous réserve | Brouillon créé dans `docs/release-notes-v1.1.0.md`; à finaliser avant Gate Production |
| Rollback Production | À vérifier | Par tag immuable + backup ; drill significatif à planifier |
| Validation PO | À obtenir | Requise avant Gate Production |
| Validation Release Manager | À obtenir | Requise avant Gate Production |

## 6. Risques et réserves

| Risque | Niveau | Traitement |
|--------|--------|------------|
| RSV-RM-01 — accumulation Staging | Moyen | Périmètre RC explicite Quittances + Patrimoine ; Sprint 3 exclu |
| RSV-RM-02 — dérive Staging/Production | Moyen | Comparer tag Staging candidat et tag Production avant Gate |
| RSV-RM-03 — rollback non testé sur release ultérieure | Moyen | Planifier drill ou réserve explicite au Gate Production |
| RSV-RM-04 — plusieurs Epics non validés | Moyen | Validation PO/Release Manager du périmètre RC obligatoire |
| UX/UI Gate 02A non rétroactif | Mineur | Pas de nouveau lot UI avancé dans cette RC ; à traiter Sprint 3 |

## 7. Pré-checklist Gate Production

| Élément | Statut |
|---------|--------|
| Périmètre Production identifié | OK |
| Version candidate identifiée | OK |
| Artefact / tag final à identifier | À faire |
| Éléments candidats vérifiés en Staging | OK pour Patrimoine ; à confirmer pour Quittances dans le smoke / recette |
| Smoke tests Staging | OK pour parcours couvert ; compléter si scénario quittance requis |
| Release notes `1.1.0` | Brouillon créé, à finaliser |
| Validation PO | À obtenir |
| Validation Release Manager | À obtenir |
| Rollback Production | À confirmer / tester ou réserver |
| Gate Production | GO sous réserve acceptée |

## 8. Décision

**Release candidate `1.1.0-rc.1` préparée.**

Statuts :

- `STAGING_DEPLOYED` : atteint pour le lot Patrimoine.
- `PRODUCTION_READY` : atteint par Gate Production GO sous réserve acceptée.
- `PRODUCTION_DEPLOYED` : atteint le 2026-06-23 (`sha-05424aa3`, smoke Production 47/0).

## 9. Prochaines actions

1. Surveiller la Production `1.1.0` après déploiement.
2. Planifier un drill rollback dédié à une prochaine release significative.
3. Cadrer le prochain lot `[Non publié]` avant tout nouveau développement.
