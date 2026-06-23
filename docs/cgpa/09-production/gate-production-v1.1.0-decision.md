# Gate Production v5.3 — Release `1.1.0`

| Champ | Valeur |
|-------|--------|
| Date | 2026-06-23 |
| Release candidate | `1.1.0-rc.1` |
| Version cible | `1.1.0` |
| Périmètre | Quittances de loyer + Patrimoine Sprint 1/2 |
| Base Production actuelle | `1.0.0` (`sha-73359c5c`) |
| Artefact candidat initial | `sha-1d6db31` (merge PR #77, non publié GHCR) |
| Artefact déployé | `sha-05424aa3` (push `main` post-PR #77, publié GHCR) |
| Environnement source | Staging |
| Environnement cible | Production `https://loyertracker.loyerpro.org` |
| Décision | **GO sous réserve acceptée** |
| Statut CGPA v5.3 | `PRODUCTION_READY` puis `PRODUCTION_DEPLOYED` |

## 1. Objet

Ce Gate Production v5.3 statue l'autorisation de mise en Production de la release `1.1.0`, préparée via la Release candidate `1.1.0-rc.1`.

Ce document trace le Gate Production et son exécution postérieure. Après validation utilisateur du next step, la mise en Production a été exécutée le 2026-06-23 et les documents `docs/prod-state.md`, `docs/release-notes-v1.1.0.md` et `docs/project-state.md` ont été mis à jour pour marquer `PRODUCTION_DEPLOYED`.

## 2. Périmètre Production

Inclus :

- Quittances de loyer :
  - ventilation loyer hors charges / provision de charges ;
  - profil bailleur avec adresse ;
  - génération PDF quittance / avis d'échéance ;
  - téléchargement frontend.
- Patrimoine Sprint 1 :
  - niveau `Patrimoine` ;
  - typologie `TypeBien` ;
  - rattachement obligatoire des biens ;
  - migration V12.
- Patrimoine Sprint 2 :
  - affectation patrimoine ;
  - héritage dynamique ReBAC ;
  - liste effective gestionnaire ;
  - garde RS-06 ;
  - migration V13.
- Correctifs de stabilisation :
  - honoraires patrimoine, migration V14 ;
  - smoke Staging aligné V14, PR #77.

Exclus :

- Sprint 3 Patrimoine.
- `EXCLUSION` complète.
- UX avancée de périmètre effectif.
- Export/effacement RGPD.

## 3. Checklist Gate Production

### Identification

| Critère | Statut | Preuve |
|---------|--------|--------|
| Périmètre Production identifié | OK | Release fonctionnelle `1.1.0` |
| Version SemVer identifiée | OK | `1.1.0` |
| Artefact, commit ou image identifié | OK | Candidat initial `sha-1d6db31`; tag GHCR effectivement publié et déployé `sha-05424aa3` |
| Environnement source identifié | OK | Staging |
| Environnement cible identifié | OK | Production |

### Preuves Staging

| Critère | Statut | Preuve |
|---------|--------|--------|
| Éléments candidats vérifiés en Staging | OK sous réserve | Patrimoine prouvé par Gate Staging v5.3 ; Quittances acceptées par preuves CI/changelog et absence d'écart ouvert |
| Statut Staging renseigné | OK | Patrimoine `STAGING_DEPLOYED` |
| Smoke tests Staging | OK | 47 PASS / 0 FAIL après PR #77 |
| Défauts bloquants résolus | OK | R-S04-1, V14 et compteur Flyway fermés |
| Accumulation Staging analysée | OK | Périmètre RC explicite ; Sprint 3 exclu |

### Validation fonctionnelle

| Critère | Statut | Preuve |
|---------|--------|--------|
| Release fonctionnelle validée | OK | Décision PO `GO` du 2026-06-23 |
| Validation Product Owner | OK | `GO` utilisateur confirmé |
| Validation Release Manager | OK sous réserve | Release Manager autorise Production sous réserves listées |
| Release notes disponibles | OK | `docs/release-notes-v1.1.0.md` |
| Changelog disponible | OK | `CHANGELOG.md` section `[Non publié]` |

### Contrôles techniques et DevSecOps

| Critère | Statut | Preuve |
|---------|--------|--------|
| Build / artefact Production vérifiable | OK | CI verte PR #74/#76/#77 ; Packaging Docker du push `05424aa3df...` ; tag `sha-05424aa3` vérifié sur GHCR |
| Tests critiques OK | OK | Backend, frontend, smoke Staging |
| Contrôles DevSecOps disponibles | OK | Gitleaks, Trivy, CodeQL, SonarQube dans CI |
| Migrations Production préparées | OK sous réserve | V11 à V14 identifiées ; restauration backup exigée comme rollback données |
| Observabilité minimale définie | OK | Monitoring/alerting Production existants depuis `1.0.0` |
| Secrets Production non exposés | OK | Secrets hors dépôt, `.env` hôte |

### Rollback

| Critère | Statut | Preuve |
|---------|--------|--------|
| Stratégie rollback documentée | OK | Redéploiement tag Production précédent + restauration DB |
| Responsable rollback identifié | OK | Release Manager / DevSecOps Lead |
| Conditions de déclenchement définies | OK | Smoke prod KO, incident critique, migration bloquante, alerte sécurité |
| Restauration testée ou réserve acceptée | OK sous réserve | Drill backup/restore déjà prouvé ; pas de drill spécifique V11-V14 |
| Données et migrations prises en compte | OK sous réserve | V11-V14 nécessitent restauration backup si rollback données |

## 4. Réserves acceptées

| Réserve | Niveau | Acceptation |
|---------|--------|-------------|
| RP-11-1 — preuve Staging Quittances par équivalence | Mineur | Acceptée : PR #70/#71 mergées, CI verte, périmètre documenté |
| RP-11-2 — rollback V11-V14 par restauration backup, sans drill spécifique `1.1.0` | Moyen | Acceptée : procédure backup/restore prouvée, backup pré-déploiement obligatoire |
| RP-11-3 — release notes à finaliser après déploiement effectif | Mineur | Acceptée : brouillon disponible, section traçabilité à compléter post-déploiement |

## 5. Avis des sous-agents

| Sous-agent | Avis |
|------------|------|
| Governance Officer | GO sous réserve : Gate v5.3 tracé, périmètre explicite, Production non automatique |
| Enterprise Architect | GO : migrations et ReBAC cohérents, Production distincte de Staging |
| DevSecOps Lead | GO sous réserve : backup pré-déploiement requis, smoke prod obligatoire après déploiement |
| Release Manager | GO sous réserve : release `1.1.0` autorisée, déploiement à tracer strictement |

## 6. Décision

**GO sous réserve acceptée — Gate Production v5.3 validé pour la release `1.1.0`.**

Statuts :

- `PRODUCTION_READY` : atteint.
- `PRODUCTION_DEPLOYED` : atteint le 2026-06-23 après déploiement `sha-05424aa3` et smoke Production 47 PASS / 0 FAIL.

## 7. Conditions d'exécution de la mise en Production

Avant déploiement :

1. Tag GHCR initial `sha-1d6db31` indisponible ; tag réel publié identifié : `sha-05424aa3`.
2. Backup Production pré-déploiement exécuté et vérifié : `loyertracker-20260623-150659.dump`.
3. Rollback préparé vers `sha-73359c5c`.

Pendant déploiement :

1. Déployer `LOYERTRACKER_TAG=sha-05424aa3`.
2. Vérifier 4/4 services applicatifs `healthy`.
3. Vérifier Flyway V1 à V14.
4. Exécuter smoke Production : 47 PASS / 0 FAIL.

Après déploiement :

1. `docs/prod-state.md` mis à jour.
2. `docs/release-notes-v1.1.0.md` finalisé.
3. `docs/project-state.md` mis à jour.
4. `PRODUCTION_DEPLOYED` marqué après contrôles post-déploiement OK.
