# Rapport Validation Finale — Release `1.7.0`

| Champ | Valeur |
|---|---|
| Date | 2026-07-03 |
| `PRODUCTION_DEPLOYED` | **2026-07-03 ~13:35 UTC** |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Tag en Production | `sha-6a358eb6` |
| Digest API | `sha256:485c8574cca057d4e00f3c0de640faf4ad8b378c302604b76a752563eb98dfba` |
| Digest Web | `sha256:70ae97f2eda455b5c9640cc33aeb6ea4abda9131222b3f948a5ea29768bca5c5` |
| Verdict | **PASS — `PRODUCTION_DEPLOYED` atteint** |

## 1. Smoke test Production

### Prérequis Keycloak

`bailleur-test@test.local` (`c7296c69-…`) : réactivé temporairement (`enabled=true`), pattern
identique à `1.2.1`→`1.6.0` — redésactivé après le run (§2).

### Premier essai — 58 PASS / 1 FAIL puis échec en cascade

Le premier lancement a échoué immédiatement : `FAIL Flyway : 20 migrations (attendu 19)` puis
`FAIL JWT bailleur KO` (le script s'arrête sur ce second échec). Deux causes distinctes,
diagnostiquées et corrigées dans la même session :

1. **Script de smoke non synchronisé** : le dépôt sur `loyertracker-prod-server` était resté à
   `b824a0e` (Sprint 6/7 era), 40 fichiers et le correctif du compteur Flyway (PR #158) en retard
   sur `main`. Corrigé par `git fetch && git merge --ff-only origin/main` (aucun conflit,
   fast-forward propre).
2. **`bailleur-test@test.local` désactivé** (`enabled=false`, état normal entre déploiements) —
   le smoke ne peut pas obtenir de JWT pour un compte désactivé. Réactivé temporairement.

### Second essai — 59 PASS / 0 FAIL

```
sudo env BASE=https://localhost:18443 CACERT=infra/nginx/certs/localhost.pem \
  COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml bash infra/smoke/smoke-stack.sh
```

| Section | Résultat |
|---|---|
| 0. Sanity (stack, **Flyway V1-V20**, pool loyertracker_api NOBYPASSRLS) | **5/5 PASS** |
| 1. JWT Keycloak réel bailleur via Nginx TLS | **2/2 PASS** |
| 2. Parcours bailleur (inscription 409, patrimoine, bien, bail) | **4/4 PASS** |
| 3. Invitation → acceptation Admin API → JWT gestionnaire | **4/4 PASS** |
| 4. Affectation patrimoine, échéances SECURITY DEFINER, pointage, honoraires | **9/9 PASS** |
| 5. Alertes (PREAVIS J+75), audit bailleur | **6/6 PASS** |
| 6. Scoping gestionnaire | **4/4 PASS** |
| 7. Isolation cross-tenant live (2e bailleur) | **9/9 PASS** |
| 9. RGPD (US-70) : export, isolation, effacement locataire | **12/12 PASS** |
| 10. Garde-fous AuthN/ports | **2/2 PASS** |
| **Total** | **59 PASS / 0 FAIL** ✅ |

Échafaudage révoqué par le script : `directAccessGrants OFF` (loyertracker-spa), confirmé.

### Vérification comportementale US-94 (Sprint 9) en Production

Le déploiement technique (`deploiement-technique-v1.7.0-report.md` §2) a déjà confirmé, par
requête SQL directe post-migration, que les 3 garanties (dont les 2 reconstituées par l'option A1
au Préflight) ont chacune un mouvement `DEPOT_INITIAL` cohérent et un `solde_actuel` égal au
montant du dépôt d'origine — **RSV-PROD-S9-01 confirmée résolue en conditions réelles**, pas
seulement au niveau du schéma. Le script de smoke lui-même n'exerce aucun endpoint garantie
(constat déjà fait au Gate Staging) ; cette vérification reste donc fondée sur la requête SQL
post-déploiement plutôt que sur un appel API live supplémentaire en Production.

### Détail Flyway (§0)

**20 migrations appliquées (V1→V20)** — V20 confirmée live par le smoke (`Flyway : 20 migrations
appliquées`), cohérent avec le déploiement technique.

## 2. Nettoyage transactionnel

### Scope identifié (RUN_ID `1783081992`)

Comptes créés par ce run — aucun orphelin d'une session antérieure détecté (vérifié par requête
ciblée sur les patrons `bailleur2-smoke-%@test.local` / `gest-smoke-%@test.local` avant nettoyage,
sans lecture de données personnelles réelles).

| Entité smoke | ID | Action |
|---|---|---|
| `bailleur-test@test.local` | `c7296c69-…` | Données smoke purgées (1 patrimoine, 1 bien, 1 bail + dépendances), bailleur conservé puis désactivé KC |
| `bailleur2-smoke-1783081992@test.local` | `82b44455-…` (DB) / `861ab8c0-…` (KC) | Données + bailleur supprimés (DB + KC) |
| `gest-smoke-1783081992@test.local` | `f2a72d46-…` (DB) / `ed9018bb-…` (KC) | Supprimé (DB + KC) |

### SQL exécuté (transaction atomique)

```sql
BEGIN;
DELETE FROM honoraire   WHERE bailleur_id IN ('c7296c69-8ffb-48fb-8f4d-cc6c4198ede4','82b44455-9fcb-4546-b536-e3acc504fa3c');
DELETE FROM paiement    WHERE bailleur_id IN ('c7296c69-8ffb-48fb-8f4d-cc6c4198ede4','82b44455-9fcb-4546-b536-e3acc504fa3c');
DELETE FROM alerte      WHERE bailleur_id IN ('c7296c69-8ffb-48fb-8f4d-cc6c4198ede4','82b44455-9fcb-4546-b536-e3acc504fa3c');
DELETE FROM audit_log   WHERE bailleur_id IN ('c7296c69-8ffb-48fb-8f4d-cc6c4198ede4','82b44455-9fcb-4546-b536-e3acc504fa3c');
DELETE FROM affectation WHERE id = 'abd0c13b-4cf2-45f4-bfd6-d254ee8f8788';
DELETE FROM invitation  WHERE id = 'be030cd3-79fe-4636-a4a7-3f67ec8127b1';
DELETE FROM bail        WHERE id = '6e23844f-d6ec-4e0e-8cba-cbd29d1087e4';
DELETE FROM bien        WHERE id = 'cd9eeb61-c31e-491a-845f-4e925f026bfb';
DELETE FROM patrimoine  WHERE id IN ('571175e7-1eb2-44b6-a51d-0de69f268e7b','8e80a6e3-e83f-413b-a791-271c770cc54d');
DELETE FROM gestionnaire WHERE id = 'f2a72d46-cb48-4bde-9624-be33993b0536';
DELETE FROM bailleur    WHERE id = '82b44455-9fcb-4546-b536-e3acc504fa3c';
COMMIT;
```

Aucune table `garantie`/`garantie_movement` affectée — les 3 garanties réelles (dont les 2
reconstituées par A1) appartiennent à `jptshilombo`, non touchées.

### Compteurs post-nettoyage

| Table | Avant | Après | Attendu |
|---|---|---|---|
| bailleurs | 3 | **2** | jptshilombo + bailleur-test ✅ |
| gestionnaires | 2 | **1** | gestionnaire réel ✅ |
| patrimoines | 3 | **1** | Patrimoine principal (jptshilombo) ✅ |
| biens | 4 | **3** | Biens jptshilombo ✅ |
| baux | 4 | **3** | Baux jptshilombo ✅ |
| affectations | 1 | **0** | ✅ |
| paiements | 84 | **75** | Paiements jptshilombo ✅ |
| honoraires | 9 | **0** | ✅ |
| alertes | 87 | **81** | Alertes jptshilombo ✅ |
| audit_log | 5 | **2** | Écritures jptshilombo ✅ |
| invitations | 1 | **0** | ✅ |
| **garanties** | 3 | **3** | **Inchangé** — 1 préexistante + 2 reconstituées A1, toutes de `jptshilombo` ✅ |

Compteurs identiques à la baseline `1.6.0` (75 paiements, 81 alertes, 2 audit_log), **garanties
passées de 1 à 3 conformément à l'option A1** (reconstitution, cf. Préflight) — aucune donnée
réelle affectée par le nettoyage, aucun résidu.

### Keycloak

| Compte | Action | Résultat |
|---|---|---|
| `bailleur2-smoke-1783081992` (`861ab8c0-…`) | Supprimé | ✅ |
| `gest-smoke-1783081992` (`ed9018bb-…`) | Supprimé | ✅ |
| `bailleur-test@test.local` (`c7296c69-…`) | Désactivé (`enabled=false`) | ✅ |
| `directAccessGrantsEnabled` (loyertracker-spa) | Révoqué (par le script + reconfirmé manuellement) | ✅ `false` |

## 3. Persistance `.env`

```
LOYERTRACKER_TAG=sha-6a358eb6
```

Déjà en place sur `loyertracker-prod-server` (modifié au déploiement technique, cf.
`deploiement-technique-v1.7.0-report.md` §1 — écart de procédure noté, backup
`.env.bak-pre-1.7.0` disponible). Vérification : `grep LOYERTRACKER_TAG .env` →
`sha-6a358eb6` ✅, permissions 600 ✅.

## 4. Contrôles post-persistance finaux

| Contrôle | Résultat |
|---|---|
| `LOYERTRACKER_TAG=sha-6a358eb6` dans `.env` | ✅ |
| 8/8 conteneurs Up, 4/4 `(healthy)`, restart=0 | ✅ |
| Actuator `{"status":"UP"}` | ✅ |
| `/healthz` (port HTTP `18080`) | ✅ `ok` |
| Prometheus 5/5 `up` | ✅ |
| Alertmanager `[]` | ✅ |

## 5. `PRODUCTION_DEPLOYED` — atteint

**2026-07-03 ~13:35 UTC** — release `1.7.0` (`sha-6a358eb6`) déployée et validée en Production.
Sprint 9 EP-12a (US-94, ledger de mouvements de garantie) désormais en Production. Anomalie de
données réelle (RSV-PROD-S9-01) résolue par reconstitution Préflight (option A1) avant la
migration, vérifiée cohérente après.

### Réserves restant ouvertes après ce déploiement

- **RSV-S9-03** (acceptée par le PO, non levée — permanente pour ce schéma) : aucun rollback
  applicatif seul viable pour revenir à `1.6.0` (`bail.depot_garantie` supprimée) ; seule la
  restauration complète du backup pré-déploiement permettrait un retour arrière.
- **RSV-S7-8-01** (non bloquante, héritée de `1.6.0`) : confirmation visuelle USD/CDF toujours
  différée faute de bail USD/CDF réel en Production.
- **RP-160-03** (non bloquante, héritée de `1.6.0`) : `CHANGELOG.md` — scinder `[Non publié]` en
  releases livrées, à traiter à la clôture de release.

Prochaines étapes :
- Hypercare T0 (immédiat), T+12, T+24
- Décision CDO → clôture `1.7.0`
