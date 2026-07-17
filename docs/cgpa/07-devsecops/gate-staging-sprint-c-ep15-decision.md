# Gate Staging v5.4.1 — Sprint C EP-15 (Bascule Bail → Locataire, US-113/114)

| Champ | Valeur |
|---|---|
| Date | 2026-07-17 |
| Candidat | merge PR #229 `359f4d6` (Sprint C EP-15, US-113/114), tag `sha-359f4d63` |
| Rollback | `sha-cba13f52` (EP-13 Fin de bail) — **non viable pour V26** (cf. §Rollback) |
| Environnement | `ai-test-server` (mutualisé), `https://loyertracker.staging.loyerpro.org` (accès direct via `https://localhost:18443` pour ce Gate) |
| Décision | **GO — `STAGING_DEPLOYED`** |

## Conditions d'entrée

| Critère | Statut | Preuve |
|---|---|---|
| GO explicite du PO sur le démarrage du Sprint C | PASS | `docs/project-state.md` §11 (2026-07-17), `cadrage-sprint-c-ep15.md` §6 |
| Sprint codé et vert | PASS | `mvn verify` 187/187, `ng build`/`ng test` 94/94 (session du 2026-07-17) |
| Vérification manuelle navigateur (dev local) | PASS | login Keycloak réel, création bien/locataire/bail, lecture correcte du nom du locataire dans l'historique |
| CI PR #229 | PASS | 7/7 checks (Backend+SonarQube, Frontend, Sécurité, Packaging Docker, CodeQL ×3) — un correctif Sonar (`java:S107`, constructeur `Bail`) a été nécessaire et pré-validé contre l'instance SonarQube réelle avant re-push |
| CI post-merge `main` | PASS | Frontend, Sécurité, Backend, Packaging Docker tous verts |
| Images GHCR | PASS | `loyertracker-api`/`loyertracker-web` `sha-359f4d63` publiées (2026-07-17 ~07:57 UTC), digests confirmés sur l'hôte |
| Sauvegarde pré-déploiement | PASS | `loyertracker-staging-20260717-075922.dump`, 554520 octets, SHA-256 `a197bd9c325eb4bdd73eb4cb93e28be6dc17f052500420ff7fa0ac34cb9d0ab2`, 799 entrées (`pg_restore --list`) + globals (1108 octets, SHA-256 `98e7db7c21278395d04aa008f89c7ebe45d302172271b805d973da1b686ddd90`) |

## STG-ISOL-01

| Contrôle | Avant | Après | Résultat |
|---|---|---|---|
| Projet Compose | `loyertracker-staging` | identique | PASS |
| Conteneurs projet | 9 (8 `loyertracker-staging-*` + `nginx-proxy-manager`), tous Up | identiques ; seuls `api`/`nginx` recréés | PASS |
| NPM mutualisé (`nginx-proxy-manager`) | running, restart=0 | running, restart=0 | PASS |
| Réseau dédié | `loyertracker-staging_loyertracker-net` | identique | PASS |
| Volumes dédiés | `loyertracker-staging_postgres-data`, `_prometheus-data` | identiques ; aucune autre ressource du host (`infra_*`/`tools_*`/`ubuntu_*`) touchée | PASS |
| Commandes exécutées | `git pull --ff-only`, `docker compose -f docker-compose.staging.yml pull/up -d --no-deps api nginx` | ciblées, aucune commande Docker globale | PASS |
| Restart counts | 0 pour tous | 0 pour tous (`api`/`nginx`/`postgres`/`keycloak` vérifiés après déploiement + smoke + nettoyage) | PASS |

**Verdict STG-ISOL-01 : PASS.** Aucun conteneur, réseau, volume ou reverse proxy tiers arrêté,
supprimé ou modifié.

## Déploiement et validation

- Dépôt hôte fast-forward `cba13f5` → `359f4d6` (38 fichiers).
- `.env` : `LOYERTRACKER_TAG` `sha-cba13f52` → `sha-359f4d63` (sauvegarde `.env.bak-pre-sprint-c-359f4d63`).
- Déploiement strictement ciblé : `api` et `nginx` recréés (avertissement Compose « conteneurs
  orphelins » du monitoring attendu et ignoré, comme aux Gates précédents — `--remove-orphans`
  jamais utilisé).
- Digests confirmés sur l'hôte : API `sha256:ea040492bb5ad6b6a72b84665e22cd47a66d79c293b874fca481d5a276afe1c8`,
  Web `sha256:e70ebc7ba7d71406edaec6f890c2f57f06ae9d7c855680e0fba01914b4251968` — identiques à GHCR.
- Flyway : **V26 appliquée (`ep15 bascule bail locataire`), total 26/26**.
- `/healthz` → 200.

### Vérification du backfill V26 (non additive) sur données réelles

Migration critique car non additive (suppression de colonnes) exécutée sur des données Staging
réelles et non synthétiques :

| Contrôle | Résultat |
|---|---|
| `bail.locataire_nom`/`locataire_email` | Colonnes absentes (0 ligne dans `information_schema.columns`) |
| Baux avec `locataire_id` renseigné | **35/35** (100 %) |
| Baux orphelins (`locataire_id` sans `Locataire` correspondant) | **0** |
| `Locataire` total après backfill | 38 (35 backfillés + 3 déjà créés via Sprint B) |
| Échantillon (5 lignes) | `nom` = valeur intégrale de l'ancien `locataire_nom`, `prenom` NULL (RSV-EP15-02 confirmé en conditions réelles) |

## Smoke Staging (63 PASS / 0 FAIL)

Script étendu pour ce Sprint (nouveau contrat de création de bail via `locataireId`, nouvel
endpoint RGPD) — invocation canonique :

```
BASE=https://localhost:18443 COMPOSE_FILE=docker-compose.staging.yml ./infra/smoke/smoke-stack.sh
```

**Résultat : 63 PASS / 0 FAIL au premier passage** (62 historiques + 1 nouvelle assertion
`POST /api/locataires`). Couverture inchangée (sanity Flyway 26/26, RLS, JWT réels, parcours
S01→S04, isolation cross-tenant, garde-fous AuthN/ports, surface publique quittances) **plus** :

- **US-113 (création de bail via `locataireId`)** : `POST /api/locataires` (201) puis
  `POST /api/biens/{id}/baux` avec `locataireId` (201) — nouveau contrat exercé de bout en bout.
- **US-114 (RGPD retargeté)** : `DELETE /api/locataires/{id}/effacement` — 403 gestionnaire, 204
  bailleur, `locataireNom` anonymisé et `locataireEmail` effacé confirmés via l'historique du bail
  et l'export RGPD, `EFFACEMENT_LOCATAIRE` tracé dans l'audit avec `entity_type = "locataire"`.

Échafaudage `directAccessGrants` révoqué automatiquement par le script (vérifié `false` après).

## Vérification manuelle navigateur (PO, 2026-07-17)

En complément du smoke automatisé, le PO a rejoué le parcours en navigateur réel contre
`https://loyertracker.staging.loyerpro.org` (connexion Keycloak standard, `bailleur-test`
activé en permanence sur Staging — contrairement à Production) :

- **Scénario A (Bailleur)** : création d'un bien, création rapide d'un `Locataire` depuis le
  formulaire de bail (« + Nouveau locataire »), sélection automatique dans le menu déroulant,
  création du bail avec `locataireId`, nom du locataire correctement affiché dans l'historique.
- **Scénario C (Gestionnaire)** : menu déroulant « Locataire » correctement peuplé (lecture
  seule, scopé au bien via `GET /api/biens/{bienId}/locataires`) pour un Gestionnaire affecté.

**Verdict PO : test concluant.** Complète la vérification automatisée (smoke 63/0 + backfill
vérifié) par une confirmation humaine en conditions réelles sur l'environnement Staging cible,
au-delà de la vérification navigateur déjà faite en local avant le Gate (cf. entrée
`project-state.md` du même jour, « Sprint C EP-15 codé et vert »).

## Nettoyage transactionnel

Scope : toutes les données accumulées sur `bailleur-test@test.local` (compte 100 % synthétique
sur Staging, jamais de données réelles) et le 2e bailleur/gestionnaire smoke de ce run
(`bailleur2-smoke-1784275721`, `gest-smoke-1784275721`). L'accumulation dépassait largement ce
seul run (résidu de sessions Gate Staging antérieures jamais nettoyé) : 303 paiements, 311
alertes, 246 entrées d'audit, 118 affectations, 38 baux, 37 biens, 36 invitations, 35 locataires,
32 garanties, 32 patrimoines, 2 mouvements de garantie et les quittances associées supprimés en
transaction unique (quittances/verification_log d'abord, pour respecter les FK). `bailleur-test`
lui-même conservé (compte permanent). Comptes Keycloak `bailleur2-smoke-1784275721`/
`gest-smoke-1784275721` supprimés. **0 résidu vérifié après nettoyage** (bail/locataire/bien/
quittance/bailleur2/gestionnaire tous à 0).

## État final de la stack

| Contrôle | Résultat |
|---|---|
| Services | 8/8 Up, 4/4 `(healthy)`, restart=0 (vérifié après déploiement, smoke et nettoyage) |
| Tag | `sha-359f4d63` (`.env` persisté, digests conformes) |
| Erreurs API depuis le déploiement | 0 ligne 5xx |
| Prometheus | 5/5 cibles `up` |
| Alertmanager | 1 alerte `BackupHeartbeatMissing` — pattern récurrent déjà qualifié à plusieurs
  reprises dans l'historique de ce projet (cron de backup hôte, sans rapport avec ce déploiement), non bloquant |
| Site (accès direct Gate) | `https://localhost:18443` → 200 |

## Avis des rôles

| Rôle | Avis |
|---|---|
| Governance Officer | GO : GO explicite du PO tracé, historique de renumérotation (V25→V26) et décisions préservés |
| Enterprise Architect | GO : `BailDto` en lecture inchangé (dérivé de `Locataire`), seule l'écriture casse le contrat ; nouvel endpoint `GET /api/biens/{bienId}/locataires` limité en lecture pour le Gestionnaire, sans élargissement RBAC non maîtrisé |
| DevSecOps Lead | GO : CI/images/backup/Flyway/backfill/STG-ISOL-01/smoke tous PASS ; correctif Sonar pré-validé contre l'instance réelle avant re-push (leçon appliquée) |
| Release Manager | GO Staging uniquement ; Production soumise à un Gate distinct, migration non additive (RSV-EP15-03) à traiter avec un Préflight renforcé (backup avant et après) |
| Chief Delivery Officer | **GO — `STAGING_DEPLOYED`** |

## Rollback

Migration V26 **non additive** (RSV-EP15-03) : un rollback applicatif vers `sha-cba13f52`
(EP-13) n'est **pas viable** une fois `bail.locataire_nom`/`locataire_email` supprimées — seule
la restauration du backup vérifié (`loyertracker-staging-20260717-075922.dump`) permet un retour
arrière complet. Même discipline que V20 (Sprint 9) et V25 avant lui. Le Préflight Production de
la release qui embarquera V26 devra vérifier un backup disponible **avant et après** la
migration.

## Statuts et suite

- `STAGING_READY` : atteint avant déploiement (CI verte, images publiées).
- `STAGING_DEPLOYED` : atteint sur `sha-359f4d63`.
- `PRODUCTION_READY` / `PRODUCTION_DEPLOYED` : non atteints.
- Prochaine étape autorisée : instruire le Gate Production du Sprint C (distinct), avec Préflight
  renforcé (backup avant/après migration non additive). Ce document n'autorise aucun déploiement
  Production.
