# Release Candidate — Hotfix `1.1.1`

| Champ | Valeur |
|---|---|
| Date de préparation | 2026-06-24 |
| Statut | **Gate Production GO sous réserve acceptée — `PRODUCTION_READY`** |
| Type | Hotfix patch SemVer |
| Version cible | `1.1.1` |
| Commit source | `0adc4941f854304a3f7412b04294615b05403707` |
| Image API | `ghcr.io/jptshilombo/loyertracker-api:sha-0adc4941` |
| Digest API | `sha256:602c9418ac9c2329cd2989045eec1f6194cac72830e3cb27794a5ee9fc429016` |
| Image Web | `ghcr.io/jptshilombo/loyertracker-web:sha-0adc4941` |
| Digest Web | `sha256:21c18e7d3f3d4656d60c8242d7550d05bbc8252dc96a4a81b5a65e3d4215c4a3` |
| Production actuelle | `1.1.0` — `sha-05424aa3` |
| Environnement source | Staging `ai-test-server` |

## Périmètre

- Création automatique d'un patrimoine actif lors de l'inscription d'un bailleur.
- Formulaire bien aligné sur le modèle Patrimoine : `patrimoineId` obligatoire et type issu du
  référentiel.
- Correctif jackson-databind 2.21.4 pour CVE-2026-54512/54513.
- Aucune migration SQL, aucun nouvel endpoint, aucun changement d'infrastructure.

Le bug distinct d'injection de `APP_CORS_ALLOWED_ORIGIN` et `APP_INVITATION_BASE_URL` dans les
fichiers Compose est explicitement exclu.

## Source canonique

Le commit `0adc494` est contenu dans `origin/main`. Le HEAD distant observé pendant la
préparation est `a33d103`, postérieur au candidat.

Les commits postérieurs au candidat portent principalement les preuves Staging, la gouvernance
et un test de visibilité. Ils ne remplacent pas le candidat, car Staging a exécuté et validé
exactement les images `sha-0adc4941`.

## Preuves CI/CD

| Contrôle | Résultat |
|---|---|
| Backend build, tests, couverture | SUCCESS |
| Frontend lint, build, tests | SUCCESS |
| Sécurité Gitleaks, SCA, Trivy API/Web | SUCCESS |
| Packaging Docker et push GHCR | SUCCESS |
| CodeQL Java/Kotlin | SUCCESS |
| CodeQL JavaScript/TypeScript | SUCCESS |
| SonarQube backend | Étape CI SUCCESS ; Quality Gate actuel OK, couverture nouvelle 93,4 % |
| SonarQube frontend | Étape CI SUCCESS ; Quality Gate actuel OK, couverture nouvelle 96,9 % |

Runs :

- CI : `https://github.com/jptshilombo/loyertracker/actions/runs/28089960893`
- CodeQL : `https://github.com/jptshilombo/loyertracker/actions/runs/28089960897`

## Preuves Staging

- Déploiement : `sha-0adc4941`.
- Services : 4/4 healthy.
- Smoke : 47 PASS / 0 FAIL.
- Vérification navigateur réelle : authentification Keycloak, chargement des patrimoines/types,
  création d'un bien via le formulaire Angular, réponse 201.
- Nettoyage post-test confirmé et smoke 47/0 rejoué.

## Delta avec Production

Le delta applicatif depuis `sha-05424aa3` est limité à :

- `backend/pom.xml` : jackson-databind 2.21.4 ;
- `InscriptionService` et tests associés ;
- dashboard bailleur, service S02 et tests associés.

Le reste du delta est documentaire et de gouvernance. Aucun fichier Compose, script
d'infrastructure, workflow GitHub Actions ou migration Flyway n'est modifié dans le candidat.

## Version

La version de release retenue est `1.1.1`, conforme à un correctif rétrocompatible. Les versions
techniques historiques `1.0.0` présentes dans `backend/pom.xml` et `frontend/package.json` ne
sont pas modifiées dans cette préparation : les changer créerait un nouvel artefact distinct qui
devrait repasser CI et Staging.

## Rollback

- Rollback applicatif : redéployer `sha-05424aa3`.
- Données : aucune migration ni transformation de données ; aucun rollback de schéma attendu.
- Une sauvegarde pré-déploiement reste obligatoire à l'Étape 3.

## Risques ouverts

- `RSV-STG-01` reste ouverte ; elle n'est pas levée rétroactivement.
- Bug CORS Compose hors périmètre, suivi dans un lot distinct.
- Les commits postérieurs à `0adc494` ne doivent pas être substitués silencieusement au candidat.

## Gate Production

Gate Production accéléré statué **GO sous réserve acceptée** le 2026-06-24. Décision : `docs/cgpa/09-production/gate-production-v1.1.1-hotfix-decision.md`. Aucun déploiement effectué.

## Décision Étape 1

**CANDIDAT RECEVABLE.**

Cette décision clôt l'Étape 1. Elle autorise uniquement la production du plan détaillé de
l'Étape 2 — Gate Production accéléré. Elle n'autorise aucun déploiement.
