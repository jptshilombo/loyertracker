# Gate Production accéléré — Hotfix `1.1.1`

| Champ | Valeur |
|---|---|
| Date | 2026-06-24 |
| Type | Hotfix |
| Version | `1.1.1` |
| Commit | `0adc4941f854304a3f7412b04294615b05403707` |
| Artefact | API et Web `sha-0adc4941` |
| Production actuelle | `1.1.0` — `sha-05424aa3` |
| Décision | **GO sous réserve acceptée** |
| Statut | **`PRODUCTION_READY` puis `PRODUCTION_DEPLOYED`** |

## 1. Objet

Statuer l'autorisation Production du Hotfix patrimoine/bien et du correctif jackson-databind,
sans exécuter de sauvegarde ni de déploiement.

## 2. Périmètre

Inclus :

- patrimoine principal créé à l'inscription bailleur ;
- formulaire bien avec patrimoine et type obligatoires ;
- jackson-databind 2.21.4 ;
- images immuables API/Web `sha-0adc4941`.

Exclus :

- bug CORS Compose ;
- commits postérieurs à `0adc494` ;
- Sprint 3 Patrimoine ;
- migration SQL et changement d'infrastructure.

## 3. Checklist Gate Production

### Identification

| Critère | Statut | Preuve |
|---|---|---|
| Périmètre Hotfix identifié | OK | Dossier candidat `release-candidate-v1.1.1-hotfix.md` |
| Version SemVer identifiée | OK | `1.1.1`, patch rétrocompatible |
| Commit et images identifiés | OK | Commit complet, deux images et digests GHCR |
| Source identifiée | OK | Staging `ai-test-server` |
| Cible identifiée | OK | Production dédiée `loyertracker-prod-server` |

### Preuves Staging

| Critère | Statut | Preuve |
|---|---|---|
| Candidat déployé en Staging | OK | `sha-0adc4941` |
| Services | OK | 4/4 healthy |
| Smoke | OK | 47 PASS / 0 FAIL |
| Validation navigateur | OK | Authentification, données réelles, création bien 201 |
| Nettoyage post-test | OK | Configuration nominale restaurée, smoke 47/0 |
| `STG-ISOL-01` | PASS sous réserve | Gate PASS ; `RSV-STG-01` maintenue ouverte |
| Accumulation Staging | OK | Candidat figé à `0adc494`, commits postérieurs exclus |

### Validation fonctionnelle

| Critère | Statut | Preuve |
|---|---|---|
| Incident et cause documentés | OK | Plan Hotfix patrimoine/bien |
| Hotfix validé | OK | Tests automatisés et parcours navigateur réel |
| Validation Product Owner | OK | Plan Étape 2 validé et exécution demandée le 2026-06-24 |
| Validation Release Manager | GO sous réserve | Artefact, périmètre, rollback et conditions vérifiés |
| Release notes | OK | `docs/release-notes-v1.1.1.md` |
| Changelog | OK | Section candidat `1.1.1` |

### DevSecOps et sécurité

| Critère | Statut | Preuve |
|---|---|---|
| Backend | SUCCESS | Run CI `28089960893` |
| Frontend | SUCCESS | Run CI `28089960893` |
| Gitleaks / SCA / Trivy | SUCCESS | Run CI `28089960893` |
| Packaging Docker | SUCCESS | Images `sha-0adc4941` publiées |
| CodeQL Java/Kotlin et JS/TS | SUCCESS | Run `28089960897` |
| SonarQube backend | OK | Quality Gate OK, couverture nouvelle 93,4 % |
| SonarQube frontend | OK | Quality Gate OK, couverture nouvelle 96,9 % |
| Migrations | N/A | Aucune migration V15+ ; Flyway reste V1→V14 |
| Observabilité | OK | Dispositif Production existant, inchangé |
| Secrets | OK | Aucun secret ou fichier d'environnement modifié |

### Rollback

| Critère | Statut | Preuve |
|---|---|---|
| Stratégie | OK | Redéploiement API/Web `sha-05424aa3` |
| Tag précédent API | OK | Digest `sha256:69223857edcd56a600e62d710aa9b19bc226734ac5d93dbcba396aea315e9f85` |
| Tag précédent Web | OK | Digest `sha256:6ee8ae52aed10a74d2f5e6cf555562be8d9a4dfdcac93ac5fa5b6d05ab46b2cf` |
| Responsable | OK | DevSecOps Lead, coordination Release Manager |
| Données / schéma | OK | Aucun changement de schéma ou transformation de données |
| Backup | Condition Étape 3 | Backup vérifié obligatoire avant déploiement |

## 4. Réserves et conditions

| ID | Nature | Traitement |
|---|---|---|
| RSV-STG-01 | Preuve live d'isolation à renouveler au prochain mouvement Staging | Acceptée, non bloquante, reste ouverte |
| RP-111-01 | Backup Production non encore créé | **Levée le 2026-06-24** — backup `loyertracker-20260624-140441.dump`, `pg_restore --list` OK |
| RP-111-02 | Bug CORS Compose hors périmètre | Accepté comme risque distinct ; aucune modification dans le candidat |

Les réserves n'affectent ni l'identité de l'artefact, ni les tests, ni la sécurité du candidat,
ni la disponibilité du rollback applicatif.

## 5. Avis des rôles

| Rôle | Avis |
|---|---|
| Governance Officer | **GO sous réserve** — traçabilité complète, réserves explicites |
| DevSecOps Lead | **GO sous réserve** — preuves vertes ; backup/préflight obligatoire avant déploiement |
| Enterprise Architect | **GO** — aucune migration ou dérive d'architecture |
| Release Manager | **GO sous réserve** — candidat et rollback valides, conditions Étape 3 maintenues |
| Product Owner | **GO** — plan validé et exécution du Gate demandée |

## 6. Décision finale

**Chief Delivery Officer : GO sous réserve acceptée.**

- `PRODUCTION_READY` : **atteint** pour le Hotfix `1.1.1`.
- `PRODUCTION_DEPLOYED` : **atteint le 2026-06-24** après smoke 47/0, nettoyage et persistance du tag.
- Le Gate a été suivi du préflight, backup, déploiement technique et validation finale documentés séparément.

Exécution finale : `docs/cgpa/09-production/validation-finale-v1.1.1-report.md`.
