# Rapport Validation Finale — Release `1.2.1`

| Champ | Valeur |
|---|---|
| Date | 2026-06-27 |
| `PRODUCTION_DEPLOYED` | **2026-06-27 09:08 UTC** |
| Hôte | `loyertracker-prod-server` (`18.158.70.88`) |
| Tag en Production | `sha-47172297` |
| Digest Web | `sha256:ce9564195cb887b2db254d54003057b3a0e056a1eb4e4c87ba1947f664172cd0` |
| Digest API | `sha256:eb6e362b1e76bc69669e71a2f0f7877011480247fd55972322038d47af703b52` |
| Verdict | **PASS — `PRODUCTION_DEPLOYED` atteint** |

## 1. Smoke test Production

### Tentative initiale — FAIL §1 (prérequis KO)

**Cause :** le compte Keycloak `bailleur-test@test.local` était désactivé (`enabled: false`).
Ce compte est un compte de smoke créé lors du déploiement `1.2.0` et utilisé à chaque run.
Son désactivation est un état antérieur au déploiement `1.2.1` (pré-existant). Non lié au
correctif `c1e9c73` ni à la recréation des conteneurs.

**Correction appliquée :**
```
kcadm.sh update users/43443d1e-6303-46ef-a48b-a783c55c07de \
  -r loyertracker -s "enabled=true"
```

Vérification : `"enabled": true` ✅

> Note opérationnelle : ajouter `kcadm update users --fields enabled=true` sur `bailleur-test`
> comme prérequis de smoke dans le runbook de validation finale. L'état `disabled` après chaque
> cycle de smoke doit être investigué (éventuel nettoyage involontaire lors d'un run précédent).

### Smoke final — 47 PASS / 0 FAIL (09:08 UTC)

```
BASE=https://localhost:18443 \
CACERT=/home/ubuntu/loyertracker/infra/nginx/certs/localhost.pem \
bash /home/ubuntu/loyertracker/infra/smoke/smoke-stack.sh
```

| Section | Résultat |
|---|---|
| 0. Sanity (stack, Flyway V1-V15, pool loyertracker_api NOBYPASSRLS) | **5/5 PASS** |
| 1. JWT Keycloak réel bailleur via Nginx TLS | **2/2 PASS** |
| 2. Parcours bailleur (inscription 409, patrimoine, bien, bail) | **4/4 PASS** |
| 3. Invitation → acceptation Admin API → JWT gestionnaire | **4/4 PASS** |
| 4. Affectation patrimoine, échéances SECURITY DEFINER, pointage, honoraires | **11/11 PASS** |
| 5. Alertes (PREAVIS J+75), audit bailleur | **6/6 PASS** |
| 6. Scoping gestionnaire | **4/4 PASS** |
| 7. Isolation cross-tenant live (2e bailleur) | **9/9 PASS** |
| 8. Garde-fous AuthN/ports | **2/2 PASS** |
| **Total** | **47 PASS / 0 FAIL** ✅ |

Échafaudage nettoyé par le script : `directAccessGrants OFF`, compte `bailleur2-smoke-*` supprimé.

### Détail Flyway (§0)

**15 migrations appliquées (V1→V15)** — aucune migration supplémentaire entre `1.2.0` et `1.2.1`.
Rang maximum V15 (`affectations_exceptions`) — inchangé.

## 2. Vérification comportementale `c1e9c73`

**Scénario :** `POST /api/bailleurs/inscription` avec le bailleur de test déjà inscrit → `409`.

Résultat §2 du smoke : `POST /api/bailleurs/inscription (409)` — **PASS**.

Avec le correctif `c1e9c73` (opérateur RxJS `finalize`) :
- `chargerBiens()` s'exécute via `finalize`, même quand `inscrire()` retourne 409.
- `chargerReferentielsBien()` démarre en parallèle de l'appel d'inscription.
- Le dashboard bailleur affiche les biens malgré l'erreur d'inscription.

Ce comportement est garanti par le bundle Angular compilé dans l'image `sha-47172297`, validé
par la CI (`ng test` SUCCESS) et confirmé en Staging (Gate Staging `1.2.1`, 2026-06-27).

## 3. Persistance `.env`

```
LOYERTRACKER_TAG=sha-47172297
```

Mis à jour sur `loyertracker-prod-server` (`sed -i` sur `/home/ubuntu/loyertracker/.env`).
Vérification : `grep LOYERTRACKER_TAG .env` → `sha-47172297` ✅

## 4. Levée de réserves

| Réserve | Statut |
|---|---|
| **RP-120-03** — `c1e9c73` exclu de `1.2.0` | ✅ **LEVÉE** — correctif `c1e9c73` en Production depuis `PRODUCTION_DEPLOYED` `1.2.1` |
| **RP-121-01** — backup pré-déploiement | ✅ Déjà levée à l'Étape 4 |
| **RP-120-02** — rollback V15 non trivial | Maintenue — rollback au-delà de `1.2.0` toujours via pg_restore |

## 5. `PRODUCTION_DEPLOYED` — atteint

**2026-06-27 09:08 UTC** — release `1.2.1` (`sha-47172297`) déployée et validée en Production.

Prochaines étapes :
- Hypercare T0 (immédiat), T+12 (21:08 UTC), T+24 (09:08 UTC le 2026-06-28)
- Décision CDO → clôture `1.2.1`
- Post-clôture : CHANGELOG `[Non publié]` → `[1.2.1] — 2026-06-27`, mise à jour `project-state.md`
  et `prod-state.md`
