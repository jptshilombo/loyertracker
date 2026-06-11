# Rapport d'exécution — Smoke test runtime stack complète (Keycloak + Nginx)

- **Type :** suivi technique / validation runtime (ADR-01, ADR-08)
- **Branche :** `chore/smoke-test-stack-complete` (depuis `main` @ `01b8d3f`)
- **Niveau du Plan d'Exécution :** 2 (approuvé par le PO avec les défauts A–D)
- **Agent :** Claude Opus 4.8 (1M context)
- **Date :** 2026-06-11
- **Statut :** ✅ Exécuté — **46 vérifications PASS, 0 FAIL** (2 passages complets), prêt pour revue/merge (décision PO)

## 1. Objet

Prouver **en conditions réelles**, sur la stack Docker Compose complète (Nginx TLS → Keycloak 24 →
API Spring Boot → PostgreSQL RLS), ce que ni le smoke test du 2026-06-08 (jar + PostgreSQL seul)
ni les tests d'intégration PR #18 (Testcontainers, JWT simulés MockMvc) ne couvraient :
**JWT réels émis par Keycloak**, **traversée Nginx** (ports internes non publiés, ADR-08),
**Admin API gestionnaire** dans le parcours complet, et **RLS `FORCE` sous `loyertracker_api`**
(NOSUPERUSER NOBYPASSRLS) de bout en bout, isolation cross-tenant live incluse.

## 2. Méthode (défauts A–D appliqués)

- **A — Périmètre complet S01→S04** en un passage : inscription, bien, bail, invitation/acceptation
  (Admin API réelle), affectation, échéances (`SECURITY DEFINER`), pointage, honoraires, alertes
  (dont PREAVIS), audit — côtés bailleur **et** gestionnaire.
- **B — Cross-tenant live** : 2ᵉ bailleur créé via `kcadm` (échafaudage runtime), inscrit, et
  vérifié totalement isolé du tenant 1.
- **C — Tokens** : `directAccessGrants` activé **temporairement** sur `loyertracker-spa` via
  `kcadm` (précédent R6), **révoqué automatiquement par `trap` en fin de script** — aucune
  modification versionnée du realm.
- **D — Livrable versionné** : `infra/smoke/smoke-stack.sh` (rejouable sur stack vivante : comptes
  suffixés par id de run, inscription 201|409 ; aucun secret en dur, tout provient de `.env`).
- Préparation : `.env` généré (secrets `openssl rand`, non versionné), certificat TLS auto-signé
  SAN `localhost` (mkcert absent de l'hôte → parade openssl du plan), volume PostgreSQL vierge,
  image API rebuildée (inclut le patch openssl PR #20).

## 3. Résultats

Deux passages complets : 1ᵉʳ passage **45 PASS / 0 FAIL**, 2ᵉ passage (assertions renforcées,
re-run sur stack vivante) **46 PASS / 0 FAIL**. Aucun correctif applicatif ni de migration n'a été
nécessaire : **aucun écart fonctionnel révélé**.

| # | Vérification | Résultat |
|---|---|---|
| 0 | Stack healthy ; Flyway **10 migrations** par le rôle admin ; pool JDBC API connecté **sous `loyertracker_api`** (`pg_stat_activity`) ; rôle `rolsuper=false, rolbypassrls=false` ; santé API via Nginx TLS | PASS |
| 1 | JWT réel Keycloak via Nginx, issuer `https://localhost/auth/realms/loyertracker` | PASS |
| 2 | Inscription bailleur 201 (bootstrap RLS via GUC) ; bien 201 ; bail 201 (début J−6 mois, fin J+75) | PASS |
| 3 | Invitation 201 → acceptation 201 **non authentifiée** (token = capacité) → **compte Keycloak créé par l'Admin API réelle** (`compteCree=true`) → JWT gestionnaire obtenu avec ce compte | PASS |
| 4 | Affectation POURCENTAGE 8 % (201) ; `POST /api/batch/echeances` → `{echeancesCreees:9, loyersEnRetard:6}` ; pointage RECU 900 (200) ; **honoraire période pointée = 72,00 € (8 % de 900)** ; PATCH statut par gestionnaire → **403** ; PAYE par bailleur → 200 | PASS |
| 5 | `POST /api/batch/alertes` → 6 alertes ; **PREAVIS présente** (terme à J+75, bande `]J+60;J+90]`) ; marquage lue 200 ; audit bailleur 200 (pointage + validation honoraire) | PASS |
| 6 | Gestionnaire : voit **1 bien** (l'affecté) ; `GET /api/audit` → **403** ; alertes scopées 200 | PASS |
| 7 | **Cross-tenant** : bailleur 2 inscrit (201) ; voit **0 bien**, **0 alerte** du tenant 1 ; accès directs paiements/honoraires du bien tenant 1 → **403** | PASS |
| 8 | Sans token → **401** ; **ports internes non publiés** (API joignable uniquement via Nginx 443) | PASS |

## 4. Écarts et triage

- **Aucun écart applicatif.** Les seuls correctifs du lot portent sur le script lui-même, révélés
  au 1ᵉʳ passage : comparaison d'attributs de rôle (`false,false` vs `f,f`), décodage base64url du
  JWT (padding), et assertion honoraires re-ciblée sur la **période pointée** (le tableau JSON
  n'étant pas trié). Conformes au défaut « investigation avant correctif » : triage fait, cause à
  chaque fois côté harnais de test, pas côté application.
- mkcert absent de l'hôte → certificat openssl auto-signé + `curl --cacert` (parade prévue au plan).

## 5. Échafaudages et hygiène

- `directAccessGrants` : activé puis **révoqué automatiquement** (`trap EXIT`) à chaque passage.
- Comptes de smoke (gestionnaire, bailleur 2) suffixés par id de run — données de dev, conservées.
- **Aucun secret versionné** : `.env` et certs gitignorés ; le script ne contient aucune valeur.
- Stack arrêtée en fin de campagne (`docker compose down`), volume conservé pour inspection.

## 6. Conformité CGPA & suite

- Plan d'Exécution (Niveau 2, défauts A–D) approuvé avant toute exécution.
- Risque §13 « Comportement runtime sous le rôle restreint non vérifié end-to-end » : la part
  résiduelle (« smoke test stack complète ») est **couverte** — proposition : **Fermé**.
- Hors périmètre (suivi distinct, déjà noté en R6) : navigation SPA Angular complète servie par
  Nginx ; production readiness (staging/CD/backup).
- Synchro `project-state.md` post-merge en PR de doc séparée.
