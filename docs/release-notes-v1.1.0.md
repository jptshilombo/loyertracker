# Release Notes — LoyerTracker v1.1.0

> Release notes finales de la release `1.1.0`.
> Statut : **déployée en Production le 2026-06-23 — `PRODUCTION_DEPLOYED`**.

## 1. Identification de la release

| Champ | Valeur |
|-------|--------|
| Version cible | `1.1.0` |
| Candidate | `1.1.0-rc.1` |
| Date de préparation | 2026-06-23 |
| Date de déploiement Production | 2026-06-23 |
| Tag Production | `sha-05424aa3` |
| Base Production précédente | `1.0.0` (`sha-73359c5c`) |
| Environnement source | Staging |
| Décision Production | GO sous réserve acceptée le 2026-06-23 ; déploiement exécuté et validé |

## 2. Contenu candidat

### Quittances de loyer

- Ventilation du loyer en hors charges et provision de charges.
- Profil bailleur enrichi avec adresse.
- Génération PDF à la volée de quittance et avis d'échéance.
- Téléchargement depuis le frontend.

### Patrimoine

- Niveau `Patrimoine` entre bailleur et bien.
- Typologie administrable des biens.
- Rattachement obligatoire des biens à un patrimoine.
- Affectations patrimoine.
- Héritage dynamique ReBAC pour les gestionnaires.
- Garde d'archivage RS-06.
- Correction des honoraires sur affectation patrimoine.

## 3. Migrations de données

- V11 : ventilation du loyer.
- V12 : patrimoine et typologie.
- V13 : affectations patrimoine.
- V14 : honoraires patrimoine.

## 4. Preuves

- Gate Staging v5.3 Patrimoine : GO, `STAGING_DEPLOYED`.
- Smoke Staging post-V14 : 47 PASS / 0 FAIL.
- CI GitHub des PR #74, #76, #77 : verte.
- Production `1.0.0` stable avant déploiement.
- Backup pré-déploiement : `loyertracker-20260623-150659.dump`, vérifié par `pg_restore --list`.
- Production `1.1.0` : services `api`, `nginx`, `postgres`, `keycloak` healthy après déploiement.
- Smoke Production post-déploiement : 47 PASS / 0 FAIL.
- Contrôles post-smoke : compte `bailleur-test@test.local` désactivé, `directAccessGrants=false`.

## 5. Rollback

Rollback applicatif par redéploiement du tag immuable Production précédent `sha-73359c5c`.

Rollback données : restauration PostgreSQL via `infra/backup/restore-postgres.sh` depuis le backup pré-déploiement `loyertracker-20260623-150659.dump`.

Réserve RP-11-2 acceptée : pas de drill dédié `1.1.0`, mais procédure backup/restore déjà prouvée et backup pré-déploiement vérifié.

## 6. Statut Gate

- Gate Staging : GO pour Patrimoine.
- Gate Production : GO sous réserve acceptée (`PRODUCTION_READY`).
- Production : déployée et validée (`PRODUCTION_DEPLOYED`) le 2026-06-23 avec le tag `sha-05424aa3`.
