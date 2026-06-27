# Plan d'Exécution — Release `1.2.1`

| Champ | Valeur |
|---|---|
| Date de préparation | 2026-06-27 |
| Statut | **Étape 5 PASS (2026-06-27) — `sha-47172297` actif en Production — En attente décision Validation finale** |
| Type | Release PATCH — correctif frontend uniquement |
| Version cible | `1.2.1` |
| Commit applicatif unique | `c1e9c735e39c0375b907be9da3302e67f5cb10d4` |
| Production actuelle | `1.2.0` — `sha-5bf187af` |
| Rollback disponible | `sha-5bf187af` (sans pg_restore — aucune migration supplémentaire) |
| Autorisation de départ | Décision de clôture `1.2.0` — CDO GO (2026-06-26), réserve RP-120-03 |

## 1. Objet et périmètre

### Objet

La release `1.2.1` solde la réserve **RP-120-03** ouverte à la clôture de `1.2.0` : le commit
`c1e9c73` (`fix(dashboard): charge les biens même si l'inscription échoue`) était présent sur
`main` au moment du Gate Staging `1.2.0` mais a été exclu de l'artefact de Production par
décision CDO (Option A du Gate Production `1.2.0`) pour rester dans le périmètre Staging validé.

### Périmètre — strictement délimité

**Inclus :**
- `c1e9c73` — correctif Angular frontend (`dashboard.component.ts`, +5/-3 lignes) : `chargerBiens()`
  et `chargerReferentielsBien()` étaient appelés uniquement dans le callback `next` de `inscrire()` ;
  une erreur d'inscription (401, 500, réseau) laissait le tableau de bord vide. `finalize` garantit
  que `chargerBiens()` s'exécute en succès comme en erreur ; `chargerReferentielsBien()` est lancé
  immédiatement en parallèle.

**Exclus (aucune modification) :**
- Backend Spring Boot — aucun changement.
- Migrations Flyway — aucune migration supplémentaire ; V15 reste le rang maximum (15/15).
- Infra Compose — aucune variable, aucun réseau, aucun volume modifié.
- Sécurité / RLS — aucun impact.

### Différences structurelles avec `1.2.0`

| Aspect | `1.2.0` | `1.2.1` |
|---|---|---|
| Scope | Sprint 3 backend (US-85, V15, RS-04) + CORS Compose | Correctif Angular frontend pur |
| Nouvelles migrations | V15 (additive, rollback via pg_restore) | Aucune |
| Rollback schéma | Non trivial — pg_restore requis | **Non requis** — rollback applicatif seul suffisant |
| Risque de rollback | RP-120-02 maintenu | Simple retour à `sha-5bf187af` |
| Smoke coverage | Backend + CORS | Frontend comportement dashboard |

## 2. Principe d'exécution obligatoire

Aucune étape ci-dessous ne peut être exécutée directement. Avant chaque étape une décision
explicite est requise. La validation d'une étape n'autorise pas automatiquement la suivante.

Toute commande Docker exécutée sur le Staging mutualisé (`ai-test-server`) doit être ciblée
sur le projet `loyertracker-staging` uniquement — conformément à CLAUDE.md et à l'ADR-STG-001.

## 3. Étapes séquentielles

---

### Étape 1 — Identification du candidat et vérification CI

**Objectif :** identifier le tag GHCR immuable candidat pour `1.2.1`, vérifier que la CI est
verte et que le périmètre est conforme.

**Contenu du plan d'étape :**
- Tag candidat à identifier parmi les CI sur `origin/main` après `c1e9c73` :
  - Candidat naturel : `sha-47172297` (HEAD actuel — commits documentaires post-`c1e9c73`,
    code applicatif identique à `sha-c1e9c735`)
  - Alternative : `sha-c1e9c735` (commit applicatif direct)
  - Règle de sélection : tag le plus récent dont la CI GitHub Actions est SUCCESS (Backend,
    Frontend, Sécurité, CodeQL, Packaging Docker).
- Vérifier les digests GHCR `api` et `web` du tag retenu.
- Vérifier que `frontend/src/app/bailleur/dashboard/dashboard.component.ts` est la seule
  différence applicative par rapport à `sha-5bf187af`.
- Compléter `docs/release-notes-v1.2.1.md` et la section `[Non publié]` du `CHANGELOG.md`.

**Contraintes :**
- Aucun accès Production, aucun déploiement Staging.
- Aucune commande Docker sur quelque hôte que ce soit.

**Sortie attendue :** dossier candidat `docs/cgpa/09-production/release-candidate-v1.2.1.md`,
tag et digests consignés, CI SUCCESS vérifiée.

**Statut : EXÉCUTÉE le 2026-06-27 — candidat recevable.** Dossier : `release-candidate-v1.2.1.md`. Décision : candidat `sha-47172297` retenu, CI SUCCESS, diff +1 fichier applicatif confirmé.

---

### Étape 2 — Gate Staging `1.2.1`

**Objectif :** déployer le candidat sur `ai-test-server`, vérifier STG-ISOL-01 en live et
exécuter le smoke 47/0, puis statuer le Gate Staging v5.3.

**Contenu du plan d'étape :**
- **STG-ISOL-01 (pré-déploiement)** : `docker ps` sur `ai-test-server` — compter et noter les
  conteneurs `loyertracker-staging-*` présents avant le déploiement.
- **Déploiement :** `LOYERTRACKER_TAG=sha-<candidat>` + `docker compose -f docker-compose.staging.yml
  up -d --pull always` ciblé sur le projet `loyertracker-staging`. Aucune commande Docker globale.
- **Contrôles post-déploiement :**
  - 4/4 services `(healthy)`, restart count = 0.
  - Flyway : 15 migrations (V1→V15 — aucune migration supplémentaire par rapport à `1.2.0`).
  - CORS vars injectées (`APP_CORS_ALLOWED_ORIGIN`).
  - `GET /api/actuator/health` → `{"status":"UP"}`.
- **STG-ISOL-01 (post-déploiement)** : `docker ps` — mêmes conteneurs `loyertracker-staging-*`,
  aucun autre projet affecté.
- **Smoke :** `infra/smoke/smoke-stack.sh` → 47 PASS / 0 FAIL.
- **Vérification comportementale :** appeler manuellement l'endpoint d'inscription avec un compte
  déjà existant (409 attendu) et vérifier que le dashboard charge les biens malgré l'erreur
  (comportement corrigé par `c1e9c73`).
- **`STAGING_DEPLOYED`** : atteint après smoke 47/0.

**Rollback Staging :** retour à `sha-5bf187af` (tag `1.2.0`, déjà validé Staging le 2026-06-25).

**Document à créer :** `docs/cgpa/07-devsecops/gate-staging-v1.2.1-decision.md`

**Statut : EXÉCUTÉE le 2026-06-27 — GO, `STAGING_DEPLOYED`.** Tag `sha-47172297` déployé, STG-ISOL-01 PASS live, Flyway 15/15, smoke **47/0**. Décision : `gate-staging-v1.2.1-decision.md`.

---

### Étape 3 — Gate Production `1.2.1`

**Objectif :** statuer l'autorisation de mise en Production de `1.2.1` → `PRODUCTION_READY`.

**Contenu du plan d'étape (checklist Gate Production v5.3) :**

*Identification :*
- Version : `1.2.1` (PATCH SemVer — correctif pur, aucune fonctionnalité nouvelle).
- Commit et artefact : tag retenu à l'Étape 1, digests immuables.
- Source : Staging `ai-test-server` ; cible : `loyertracker-prod-server`.

*Preuves Staging :*
- Gate Staging `1.2.1` : GO (Étape 2).
- STG-ISOL-01 : PASS live (Étape 2).
- Smoke 47/0 et vérification comportementale dashboard (Étape 2).

*Validation fonctionnelle :*
- Correctif validé par le PO (comportement dashboard).
- Release notes `docs/release-notes-v1.2.1.md` disponibles.
- `CHANGELOG.md` section `[Non publié]` prête (à promouvoir après `PRODUCTION_DEPLOYED`).

*DevSecOps :*
- CI SUCCESS sur le tag candidat (CodeQL, Backend, Frontend, Sécurité, Packaging).
- Pas de nouvelle migration — pas de risque Flyway additionnel.
- Digests GHCR immuables vérifiés.

*Rollback :*
- **Rollback applicatif simple : retour à `sha-5bf187af`** (Production `1.2.0`).
- Aucun `pg_restore` requis (pas de nouvelle migration entre `1.2.0` et `1.2.1`).
- RP-120-02 reste maintenue (rollback V15 via pg_restore toujours non trivial si rollback
  complet au-delà de `1.2.0`).

**Statut attendu :** `PRODUCTION_READY` atteint. `PRODUCTION_DEPLOYED` non atteint.

**Document à créer :** `docs/cgpa/09-production/gate-production-v1.2.1-decision.md`

**Statut : EXÉCUTÉE le 2026-06-27 — GO sous réserve, `PRODUCTION_READY`.** Réserve RP-121-01 (backup pré-déploiement) bloquante. Décision : `gate-production-v1.2.1-decision.md`.

---

### Étape 4 — Préflight + backup Production

**Objectif :** vérifier l'état sain de la Production et créer le backup pré-déploiement.

**Contenu du plan d'étape (lecture seule + backup) :**
- Contrôles de santé : 8/8 conteneurs `Up` (healthy), restart count = 0, `LOYERTRACKER_TAG=sha-5bf187af`.
- Flyway : 15/15 (V1→V15 inchangé).
- Capacité hôte : disque, mémoire, charge.
- Prometheus : 5/5 cibles `up`, Alertmanager `[]`.
- **Backup** : `pg_dump -Fc` base + `pg_dumpall --globals-only` → vérification `pg_restore --list`,
  permissions 600, SHA-256 consigné.
- Condition bloquante : backup vérifié avant tout déploiement.

**Nota bene :** le backup pré-`1.2.1` protège contre un rollback éventuel. Si rollback vers
`sha-5bf187af` (aucune migration supplémentaire entre `1.2.0` et `1.2.1`), la restauration
applicative seule suffit — le backup reste la procédure de sécurité standard.

**Document à créer :** `docs/cgpa/09-production/preflight-backup-v1.2.1-report.md`

**Décision requise avant exécution :** décision distincte après Gate Production (Étape 3).

**Statut : EXÉCUTÉE le 2026-06-27 — PASS, RP-121-01 LEVÉE.** 8/8 conteneurs Up, 4/4 healthy, restart=0. Flyway 15/15. Actuator UP. Prometheus 5/5, Alertmanager 0. Capacité OK. Backup `loyertracker-20260627-085033.dump` (311 Kio, 730 entrées pg_restore, permissions 600, SHA-256 consigné). Rapport : `preflight-backup-v1.2.1-report.md`.

---

### Étape 5 — Déploiement technique `1.2.1`

**Objectif :** promouvoir l'artefact `1.2.1` en Production.

**Contenu du plan d'étape :**
- `LOYERTRACKER_TAG=sha-<candidat> docker compose -f docker-compose.prod.yml up -d --pull always nginx`
  — uniquement le service **`nginx`** (contient le bundle Angular compilé).
  Le service `api` reste inchangé (`sha-5bf187af`) : aucun changement backend entre `1.2.0` et `1.2.1`.
- Vérifier les digests de l'image `web` tirée (`docker inspect`).
- Contrôles post-déploiement : `nginx` `(healthy)`, restart count = 0, Actuator API inchangé.
- Prometheus 5/5 up, Alertmanager `[]`.
- **`PRODUCTION_DEPLOYED`** non atteint — réservé à la validation finale (Étape 6).

**Rollback immédiat :** `LOYERTRACKER_TAG=sha-5bf187af docker compose -f docker-compose.prod.yml
up -d nginx` — retour instantané (aucune migration à réverter).

**Document à créer :** `docs/cgpa/09-production/deploiement-technique-v1.2.1-report.md`

**Décision requise avant exécution :** décision distincte après Étape 4.

**Statut : EXÉCUTÉE le 2026-06-27 — PASS.** Tag `sha-47172297` déployé. `nginx` + `api` recréés (api : dépendance + --pull always, code Java identique). Digests conformes. 8/8 Up, 4/4 healthy, restart=0. Web HTTP 200, Actuator UP, Prometheus 5/5. Alerte `BackupHeartbeatMissing` pré-existante (Pushgateway volatil post-reboot serveur — hors périmètre déploiement). Rapport : `deploiement-technique-v1.2.1-report.md`.

---

### Étape 6 — Validation finale, hypercare et clôture

**Objectif :** valider le comportement en Production, marquer `PRODUCTION_DEPLOYED`, conduire
l'hypercare 24 h et clore la release `1.2.1`.

**Validation finale :**
- Smoke `infra/smoke/smoke-stack.sh` → **47 PASS / 0 FAIL** (Flyway 15/15, pas de migration
  supplémentaire).
- Vérification comportementale spécifique `c1e9c73` :
  - Tenter `POST /api/bailleurs/inscription` avec un bailleur déjà inscrit → 409.
  - Vérifier que le dashboard bailleur charge les biens malgré l'erreur 409.
- Nettoyage des comptes/données de test.
- Révoquer `directAccessGrants` si activé pour le test.
- Persister `LOYERTRACKER_TAG=sha-<candidat>` dans `.env`.
- **`PRODUCTION_DEPLOYED` atteint.**

**Hypercare 24 h :**
- T0 : immédiatement après `PRODUCTION_DEPLOYED`.
- T+12 : dans la fenêtre T0+12 h ±30 min.
- T+24 : dans la fenêtre T0+24 h ±30 min.
- Critères de suspension (tout checkpoint FAIL) : rollback vers `sha-5bf187af` (simple, sans
  pg_restore).

**Clôture :**
- Décision CDO GO → release `1.2.1` CLÔTURÉE.
- Promouvoir `CHANGELOG.md` section `[Non publié]` → `[1.2.1] — YYYY-MM-DD`.
- Mettre à jour `docs/project-state.md` (bandeau, §1, §3A).
- Mettre à jour `docs/prod-state.md`.
- RP-120-03 **levée** (correctif `c1e9c73` en Production).
- RP-120-02 **maintenue** (rollback V15 via pg_restore toujours non trivial si retour à `1.1.x`).

**Documents à créer :**
- `docs/cgpa/09-production/validation-finale-v1.2.1-report.md`
- `docs/cgpa/09-production/plan-etape-hypercare-v1.2.1.md`
- `docs/cgpa/09-production/cloture-release-v1.2.1.md`
- `docs/release-notes-v1.2.1.md`

**Décision requise avant exécution :** décision distincte après Étape 5.

---

## 4. Registre des documents du cycle `1.2.1`

| Document | Étape | Statut |
|---|---|---|
| `plan-execution-v1.2.1.md` (ce fichier) | — | En attente approbation PO |
| `release-candidate-v1.2.1.md` | 1 | À créer |
| `docs/release-notes-v1.2.1.md` | 1 | À créer |
| `gate-staging-v1.2.1-decision.md` | 2 | À créer |
| `gate-production-v1.2.1-decision.md` | 3 | À créer |
| `preflight-backup-v1.2.1-report.md` | 4 | **Créé 2026-06-27 — PASS** |
| `deploiement-technique-v1.2.1-report.md` | 5 | **Créé 2026-06-27 — PASS** |
| `validation-finale-v1.2.1-report.md` | 6 | À créer |
| `plan-etape-hypercare-v1.2.1.md` | 6 | À créer |
| `cloture-release-v1.2.1.md` | 6 | À créer |
| `CHANGELOG.md` — promotion `[Non publié]` → `[1.2.1]` | 6 (post-`PRODUCTION_DEPLOYED`) | En attente |

## 5. Risques et réserves

| ID | Description | Niveau | Traitement |
|----|-------------|--------|------------|
| RP-120-02 | Rollback schéma V15 non trivial | Maintenu | Rollback `1.2.1` → `1.2.0` applicatif seul (aucune migration entre les deux) ; rollback au-delà de `1.2.0` requiert pg_restore |
| RP-120-03 | `c1e9c73` exclu de `1.2.0` | À lever | Levée après `PRODUCTION_DEPLOYED` `1.2.1` |
| RSV-STG-02 | Collision namespace/réseau/volume/port future | En surveillance | Checklist STG-ISOL-01 à l'Étape 2 |
| RSV-STG-03 | Commande Docker globale introduite accidentellement | En surveillance | Vérification pipeline et runbooks à l'Étape 2 |
| RSV-STG-04 | Dérive reverse proxy ou inventaire mutualisé | En surveillance | Revue `staging-state.md` à l'Étape 2 |

## 6. Décision PO requise

Ce plan est en attente d'approbation. Avant toute exécution (Étape 1 incluse) :

- [ ] PO valide le périmètre (`c1e9c73` uniquement — correctif dashboard Angular).
- [ ] PO confirme la séquence en 6 étapes avec point de contrôle entre chaque.
- [ ] PO confirme que aucun autre lot ou modification n'est à inclure dans `1.2.1`.

Après approbation PO, l'Étape 1 (identification candidat + vérification CI) peut démarrer.
Aucune autre étape ne peut démarrer sans son propre plan détaillé et sa propre décision.
