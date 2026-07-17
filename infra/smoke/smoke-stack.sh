#!/usr/bin/env bash
# =============================================================================
# Smoke test runtime — stack complète (Nginx TLS -> Keycloak OIDC -> API -> RLS)
#
# Prouve en conditions réelles ce que les tests d'intégration (Testcontainers,
# JWT simulés) ne couvrent pas : JWT réels émis par Keycloak, traversée Nginx,
# Admin API gestionnaire, parcours S01->S04 sous le rôle restreint
# loyertracker_api (NOSUPERUSER NOBYPASSRLS), isolation cross-tenant live.
#
# Prérequis :
#   - stack démarrée : docker compose up -d (tous les services healthy)
#   - .env présent à la racine du dépôt (jamais versionné)
#   - certificat TLS dans infra/nginx/certs/localhost.pem (SAN localhost)
#   - curl + jq sur l'hôte
#
# Échafaudage runtime (révoqué automatiquement en fin de script, via trap) :
#   - directAccessGrants activé temporairement sur le client public
#     loyertracker-spa pour obtenir des JWT sans navigateur (précédent R6).
#     AUCUNE modification versionnée du realm.
#
# Les comptes créés (gestionnaire, 2e bailleur) sont suffixés par un id de run :
# le script est rejouable sur une stack vivante sans collision.
# Aucun secret en dur : tout provient de .env.
# =============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"
# shellcheck disable=SC1091
source .env

# Cible paramétrable (lot Production Readiness 4b) : défaut = stack locale en 443.
# Sur un hôte partagé où Nginx est publié sur un port alternatif, surcharger BASE,
# p. ex. : BASE=https://localhost:18443 COMPOSE_FILE=docker-compose.staging.yml ./infra/smoke/smoke-stack.sh
BASE="${BASE:-https://localhost}"
CACERT="${CACERT:-infra/nginx/certs/localhost.pem}"
CURL=(curl -sS --cacert "$CACERT")
RUN_ID="$(date +%s)"
REALM="loyertracker"
KCADM="/opt/keycloak/bin/kcadm.sh"

PASS=0
FAIL=0
note()  { printf '\n\033[1m== %s ==\033[0m\n' "$*"; }
ok()    { PASS=$((PASS+1)); printf '  \033[32mPASS\033[0m %s\n' "$*"; }
ko()    { FAIL=$((FAIL+1)); printf '  \033[31mFAIL\033[0m %s\n' "$*"; }

# expect_status <attendu> <description> <curl args...>
# Renvoie le corps dans $BODY ; n'arrête pas le script (bilan final).
BODY=""
expect_status() {
  local want="$1" desc="$2"; shift 2
  local out code
  out=$("${CURL[@]}" -o /tmp/smoke-body.$$ -w '%{http_code}' "$@") || out="000"
  code="$out"
  BODY=$(cat /tmp/smoke-body.$$ 2>/dev/null || true)
  rm -f /tmp/smoke-body.$$
  if [[ "$code" == "$want" ]]; then ok "$desc ($code)"; else ko "$desc (attendu $want, obtenu $code) : $(echo "$BODY" | head -c 300)"; fi
}

kc() { docker compose exec -T keycloak bash -c "$*"; }

# --- Échafaudage : directAccessGrants temporaire sur loyertracker-spa --------
SPA_ID=""
scaffold_on() {
  note "Échafaudage kcadm : directAccessGrants ON (loyertracker-spa)"
  kc "$KCADM config credentials --server http://localhost:8080/auth --realm master --user \$KEYCLOAK_ADMIN --password \$KEYCLOAK_ADMIN_PASSWORD" >/dev/null
  SPA_ID=$(kc "$KCADM get clients -r $REALM -q clientId=loyertracker-spa --fields id --format csv --noquotes" | tr -d '\r')
  kc "$KCADM update clients/$SPA_ID -r $REALM -s directAccessGrantsEnabled=true"
  ok "directAccessGrants activé (client $SPA_ID)"
}
scaffold_off() {
  if [[ -n "$SPA_ID" ]]; then
    kc "$KCADM update clients/$SPA_ID -r $REALM -s directAccessGrantsEnabled=false" || true
    printf '\n  Échafaudage révoqué : directAccessGrants OFF (loyertracker-spa)\n'
  fi
}
trap scaffold_off EXIT

# token <username> <password> -> stdout access_token
token() {
  "${CURL[@]}" "$BASE/auth/realms/$REALM/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=loyertracker-spa \
    -d username="$1" -d password="$2" | jq -r .access_token
}

# =============================================================================
note "0. Sanity : stack healthy, Flyway V1-V26, pool API sous loyertracker_api"
docker compose ps --format '{{.Name}} {{.Health}}' | sed 's/^/  /'
MIG=$(docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
  "SELECT count(*) FROM flyway_schema_history WHERE success")
[[ "$MIG" == "26" ]] && ok "Flyway : 26 migrations appliquées" || ko "Flyway : $MIG migrations (attendu 26)"
ROLES=$(docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
  "SELECT DISTINCT usename FROM pg_stat_activity WHERE datname='$POSTGRES_DB' AND application_name LIKE 'PostgreSQL JDBC%'")
echo "$ROLES" | grep -q '^loyertracker_api$' && ok "Pool API connecté sous loyertracker_api" \
  || ko "Pool API : rôles JDBC = [$ROLES]"
ATTRS=$(docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
  "SELECT rolsuper||','||rolbypassrls FROM pg_roles WHERE rolname='loyertracker_api'")
[[ "$ATTRS" == "false,false" ]] && ok "loyertracker_api : NOSUPERUSER, NOBYPASSRLS" || ko "loyertracker_api : attributs $ATTRS"
expect_status 200 "Santé API via Nginx TLS" "$BASE/api/actuator/health"
echo "$BODY" | jq -e '.status=="UP"' >/dev/null && ok "Actuator status UP" || ko "Actuator : $BODY"

# =============================================================================
scaffold_on

note "1. JWT réel Keycloak (bailleur de test) via Nginx"
T_B1=$(token "bailleur-test@test.local" "$KEYCLOAK_TEST_BAILLEUR_PASSWORD")
[[ "$T_B1" != "null" && -n "$T_B1" ]] && ok "JWT bailleur obtenu" || { ko "JWT bailleur KO"; exit 1; }
ISS=$(python3 -c "import base64,json,sys; p=sys.argv[1].split('.')[1]; p+='='*(-len(p)%4); print(json.loads(base64.urlsafe_b64decode(p))['iss'])" "$T_B1")
# L'issuer est canonique (KC_HOSTNAME=localhost, sans port) et c'est précisément l'URI que l'API
# valide : on le compare à KEYCLOAK_ISSUER_URI (.env), pas à BASE — qui peut porter un port
# alternatif sur un hôte partagé (lot 4b) alors que l'issuer, lui, reste portless.
[[ "$ISS" == "$KEYCLOAK_ISSUER_URI" ]] && ok "issuer = $ISS" || ko "issuer inattendu : $ISS (attendu $KEYCLOAK_ISSUER_URI)"
H_B1=(-H "Authorization: Bearer $T_B1")

note "2. Parcours bailleur : inscription, patrimoine, bien, bail"
# 201 au 1er passage, 409 si le bailleur de test est déjà inscrit (re-run sur stack vivante)
out=$("${CURL[@]}" -o /dev/null -w '%{http_code}' -X POST "${H_B1[@]}" "$BASE/api/bailleurs/inscription") || out="000"
[[ "$out" == "201" || "$out" == "409" ]] && ok "POST /api/bailleurs/inscription ($out)" \
  || ko "POST /api/bailleurs/inscription (attendu 201|409, obtenu $out)"
expect_status 201 "POST /api/patrimoines" -X POST "${H_B1[@]}" -H 'Content-Type: application/json' \
  -d "{\"nom\":\"Patrimoine Smoke $RUN_ID\",\"adresse\":\"1 rue du Patrimoine Smoke\"}" "$BASE/api/patrimoines"
PATRIMOINE1=$(echo "$BODY" | jq -r .id)
expect_status 201 "POST /api/biens" -X POST "${H_B1[@]}" -H 'Content-Type: application/json' \
  -d "{\"adresse\":\"1 rue du Smoke Test\",\"type\":\"APPARTEMENT\",\"statut\":\"LIBRE\",\"patrimoineId\":\"$PATRIMOINE1\"}" "$BASE/api/biens"
BIEN1=$(echo "$BODY" | jq -r .id)
expect_status 201 "POST /api/locataires (Locataire Smoke)" -X POST "${H_B1[@]}" \
  -H 'Content-Type: application/json' \
  -d "{\"nom\":\"Locataire Smoke\",\"email\":\"locataire-smoke-$RUN_ID@test.local\"}" \
  "$BASE/api/locataires"
LOC1=$(echo "$BODY" | jq -r .id)
DEBUT=$(date -d "-6 months" +%Y-%m-01)
FIN=$(date -d "+75 days" +%Y-%m-%d)   # bande PREAVIS ]J+60;J+90]
expect_status 201 "POST /api/biens/{id}/baux (debut $DEBUT, fin $FIN)" -X POST "${H_B1[@]}" \
  -H 'Content-Type: application/json' \
  -d "{\"locataireId\":\"$LOC1\",\"loyerHc\":800.00,\"provisionCharges\":100.00,\"depotGarantie\":900.00,\"dateDebut\":\"$DEBUT\",\"dateFin\":\"$FIN\"}" \
  "$BASE/api/biens/$BIEN1/baux"

note "3. Invitation -> acceptation (Admin API réelle) -> JWT gestionnaire"
GEST_EMAIL="gest-smoke-$RUN_ID@test.local"
GEST_PWD="Smoke-$(openssl rand -hex 8)-Aa1"
expect_status 201 "POST /api/invitations ($GEST_EMAIL)" -X POST "${H_B1[@]}" \
  -H 'Content-Type: application/json' -d "{\"email\":\"$GEST_EMAIL\"}" "$BASE/api/invitations"
INVIT_TOKEN=$(echo "$BODY" | jq -r .token)
expect_status 201 "POST /api/invitations/{token}/acceptation (non authentifié)" -X POST \
  -H 'Content-Type: application/json' \
  -d "{\"nom\":\"Smoke\",\"prenom\":\"Gestionnaire\",\"motDePasse\":\"$GEST_PWD\"}" \
  "$BASE/api/invitations/$INVIT_TOKEN/acceptation"
GEST_ID=$(echo "$BODY" | jq -r .gestionnaireId)
echo "$BODY" | jq -e '.compteCree==true' >/dev/null && ok "Compte Keycloak créé via Admin API" \
  || ko "compteCree != true : $BODY"
T_G=$(token "$GEST_EMAIL" "$GEST_PWD")
[[ "$T_G" != "null" && -n "$T_G" ]] && ok "JWT gestionnaire obtenu (compte créé par l'Admin API)" || ko "JWT gestionnaire KO"
H_G=(-H "Authorization: Bearer $T_G")

note "4. Affectation, échéances (SECURITY DEFINER), pointage, honoraires"
expect_status 201 "POST /api/affectations patrimoine (POURCENTAGE 8%)" -X POST "${H_B1[@]}" \
  -H 'Content-Type: application/json' \
  -d "{\"patrimoineId\":\"$PATRIMOINE1\",\"gestionnaireId\":\"$GEST_ID\",\"typeHonoraires\":\"POURCENTAGE\",\"montantHonoraires\":8.00,\"dateDebut\":\"$DEBUT\"}" \
  "$BASE/api/affectations"
expect_status 200 "POST /api/batch/echeances (bailleur)" -X POST "${H_B1[@]}" "$BASE/api/batch/echeances"
echo "  -> $BODY"
expect_status 200 "GET /api/biens/{id}/paiements" "${H_B1[@]}" "$BASE/api/biens/$BIEN1/paiements"
NB_ECH=$(echo "$BODY" | jq 'length')
PERIODE=$(echo "$BODY" | jq -r 'sort_by(.periode) | .[0].periode')
[[ "$NB_ECH" -ge 1 ]] && ok "$NB_ECH échéance(s) générée(s), 1re période $PERIODE" || ko "aucune échéance générée"
expect_status 200 "PATCH pointage $PERIODE (RECU 900)" -X PATCH "${H_B1[@]}" \
  -H 'Content-Type: application/json' -d '{"montantRecu":900.00,"statut":"RECU"}' \
  "$BASE/api/biens/$BIEN1/paiements/$PERIODE/pointage"
expect_status 200 "GET /api/biens/{id}/honoraires (recalcul au pointage)" "${H_B1[@]}" "$BASE/api/biens/$BIEN1/honoraires"
NB_HONO=$(echo "$BODY" | jq 'length')
HONO_ID=$(echo "$BODY" | jq -r --arg p "$PERIODE" '.[] | select(.periode==$p) | .id')
HONO_MONTANT=$(echo "$BODY" | jq -r --arg p "$PERIODE" '.[] | select(.periode==$p) | .montant')
[[ "$NB_HONO" -ge 1 ]] && ok "$NB_HONO honoraire(s) calculé(s)" || ko "aucun honoraire calculé"
[[ "$HONO_MONTANT" == "72.00" || "$HONO_MONTANT" == "72.0" ]] \
  && ok "honoraire période $PERIODE = $HONO_MONTANT (8% de 900 encaissés)" \
  || ko "honoraire période $PERIODE = $HONO_MONTANT (attendu 72.00)"
expect_status 403 "PATCH statut honoraire par le GESTIONNAIRE -> 403" -X PATCH "${H_G[@]}" \
  -H 'Content-Type: application/json' -d '{"statut":"PAYE"}' "$BASE/api/honoraires/$HONO_ID/statut"
expect_status 200 "PATCH statut honoraire PAYE (bailleur)" -X PATCH "${H_B1[@]}" \
  -H 'Content-Type: application/json' -d '{"statut":"PAYE"}' "$BASE/api/honoraires/$HONO_ID/statut"

note "5. Alertes (génération, PREAVIS attendu) et audit"
expect_status 200 "POST /api/batch/alertes (bailleur)" -X POST "${H_B1[@]}" "$BASE/api/batch/alertes"
echo "  -> $BODY"
expect_status 200 "GET /api/alertes (bailleur)" "${H_B1[@]}" "$BASE/api/alertes"
echo "$BODY" | jq -e '[.[].type] | index("PREAVIS") != null' >/dev/null \
  && ok "alerte PREAVIS présente (terme à J+75)" || ko "PREAVIS absente : $(echo "$BODY" | jq -c '[.[].type]')"
ALERTE_ID=$(echo "$BODY" | jq -r '.[0].id')
expect_status 200 "PATCH /api/alertes/{id}/lecture" -X PATCH "${H_B1[@]}" "$BASE/api/alertes/$ALERTE_ID/lecture"
expect_status 200 "GET /api/audit (bailleur)" "${H_B1[@]}" "$BASE/api/audit"
NB_AUDIT=$(echo "$BODY" | jq 'length')
[[ "$NB_AUDIT" -ge 2 ]] && ok "$NB_AUDIT écritures d'audit (pointage + validation honoraire)" \
  || ko "audit insuffisant ($NB_AUDIT écritures)"

note "6. Scoping gestionnaire"
expect_status 200 "GET /api/biens (gestionnaire : biens affectés)" "${H_G[@]}" "$BASE/api/biens"
NB=$(echo "$BODY" | jq 'length')
[[ "$NB" == "1" ]] && ok "gestionnaire voit 1 bien (le bien affecté)" || ko "gestionnaire voit $NB bien(s)"
expect_status 403 "GET /api/audit (gestionnaire) -> 403" "${H_G[@]}" "$BASE/api/audit"
expect_status 200 "GET /api/alertes (gestionnaire, scope biens affectés)" "${H_G[@]}" "$BASE/api/alertes"

note "7. Isolation cross-tenant live (2e bailleur, échafaudage kcadm)"
B2_EMAIL="bailleur2-smoke-$RUN_ID@test.local"
B2_PWD="Smoke-$(openssl rand -hex 8)-Bb2"
kc "$KCADM create users -r $REALM -s username=$B2_EMAIL -s email=$B2_EMAIL \
   -s firstName=Bailleur2 -s lastName=Smoke -s enabled=true -s emailVerified=true" >/dev/null
kc "$KCADM set-password -r $REALM --username $B2_EMAIL --new-password '$B2_PWD'"
kc "$KCADM add-roles -r $REALM --uusername $B2_EMAIL --rolename BAILLEUR"
ok "2e bailleur créé dans Keycloak ($B2_EMAIL)"
T_B2=$(token "$B2_EMAIL" "$B2_PWD")
[[ "$T_B2" != "null" && -n "$T_B2" ]] && ok "JWT bailleur 2 obtenu" || ko "JWT bailleur 2 KO"
H_B2=(-H "Authorization: Bearer $T_B2")
expect_status 201 "POST /api/bailleurs/inscription (bailleur 2)" -X POST "${H_B2[@]}" "$BASE/api/bailleurs/inscription"
expect_status 200 "GET /api/biens (bailleur 2)" "${H_B2[@]}" "$BASE/api/biens"
NB=$(echo "$BODY" | jq 'length')
[[ "$NB" == "0" ]] && ok "bailleur 2 ne voit AUCUN bien du tenant 1" || ko "fuite : bailleur 2 voit $NB bien(s)"
expect_status 403 "GET paiements du bien tenant 1 par bailleur 2 -> 403" "${H_B2[@]}" "$BASE/api/biens/$BIEN1/paiements"
expect_status 403 "GET honoraires du bien tenant 1 par bailleur 2 -> 403" "${H_B2[@]}" "$BASE/api/biens/$BIEN1/honoraires"
expect_status 200 "GET /api/alertes (bailleur 2)" "${H_B2[@]}" "$BASE/api/alertes"
NB=$(echo "$BODY" | jq 'length')
[[ "$NB" == "0" ]] && ok "bailleur 2 ne voit aucune alerte du tenant 1" || ko "fuite : $NB alerte(s) visibles"

note "9. RGPD (US-70) : export bailleur, isolation, effacement locataire"
expect_status 200 "GET /api/bailleurs/export (bailleur 1)" "${H_B1[@]}" "$BASE/api/bailleurs/export"
echo "$BODY" | jq -e --arg bid "$BIEN1" '.biens[] | select(.bien.id==$bid)' >/dev/null \
  && ok "export contient le bien smoke" || ko "bien smoke absent de l'export"
BAIL1=$(echo "$BODY" | jq -r --arg bid "$BIEN1" '.biens[] | select(.bien.id==$bid) | .baux[0].bail.id')
[[ -n "$BAIL1" && "$BAIL1" != "null" ]] && ok "bail id récupéré depuis l'export ($BAIL1)" \
  || { ko "bail id introuvable dans l'export"; }

expect_status 200 "GET /api/bailleurs/export (bailleur 2, scope isolé)" "${H_B2[@]}" "$BASE/api/bailleurs/export"
echo "$BODY" | jq -e --arg bid "$BIEN1" '[.biens[].bien.id] | index($bid) == null' >/dev/null \
  && ok "export bailleur 2 ne contient pas le bien tenant 1" || ko "fuite : export bailleur 2 contient le bien tenant 1"

expect_status 403 "DELETE .../locataires/{id}/effacement par le GESTIONNAIRE -> 403" -X DELETE "${H_G[@]}" \
  "$BASE/api/locataires/$LOC1/effacement"
expect_status 204 "DELETE /api/locataires/{id}/effacement (effacement, bailleur)" \
  -X DELETE "${H_B1[@]}" "$BASE/api/locataires/$LOC1/effacement"

expect_status 200 "GET /api/bailleurs/export (vérification post-effacement)" "${H_B1[@]}" "$BASE/api/bailleurs/export"
LOC_NOM=$(echo "$BODY" | jq -r --arg bid "$BIEN1" '.biens[] | select(.bien.id==$bid) | .baux[0].bail.locataireNom')
LOC_EMAIL=$(echo "$BODY" | jq -r --arg bid "$BIEN1" '.biens[] | select(.bien.id==$bid) | .baux[0].bail.locataireEmail')
[[ "$LOC_NOM" == "[anonymisé]" ]] && ok "locataireNom anonymisé" || ko "locataireNom = $LOC_NOM (attendu [anonymisé])"
[[ "$LOC_EMAIL" == "null" ]] && ok "locataireEmail effacé (null)" || ko "locataireEmail = $LOC_EMAIL (attendu null)"

expect_status 200 "GET /api/audit (bailleur, vérif EFFACEMENT_LOCATAIRE)" "${H_B1[@]}" "$BASE/api/audit"
echo "$BODY" | jq -e '[.[].action] | index("EFFACEMENT_LOCATAIRE") != null' >/dev/null \
  && ok "audit_log trace EFFACEMENT_LOCATAIRE" || ko "action EFFACEMENT_LOCATAIRE absente de l'audit"

note "10. Garde-fous AuthN/ports"
expect_status 401 "GET /api/biens sans token -> 401" "$BASE/api/biens"
# Vérifie que le service api de CE projet compose n'expose aucun port sur l'hôte
# (robuste sur un hôte partagé où d'autres conteneurs peuvent occuper 8080 — lot 4b).
# Selon la version de Compose, `port` renvoie soit du vide, soit "invalid IP:0" quand
# aucune publication n'existe : on extrait le port hôte (dernier champ) et on considère
# 0 ou vide comme "non publié". Une vraie publication donnerait "0.0.0.0:<port>".
API_HOSTPORT=$(docker compose port api 8080 2>/dev/null | awk -F: 'NF{print $NF}')
if [[ -n "$API_HOSTPORT" && "$API_HOSTPORT" != "0" ]]; then
  ko "port API interne 8080 publié sur l'hôte:$API_HOSTPORT (devrait rester interne)"
else
  ok "ports internes non publiés (API joignable uniquement via Nginx)"
fi

note "11. Vérification publique des quittances (US-102/104) : surface non authentifiée, sans oracle"
# L'endpoint public (permitAll ciblé, ADR-15 D5) est joignable SANS Authorization et répond
# toujours de façon indifférenciée : un id inconnu + un token forgé donnent le même verdict
# INVALIDE, sans divulguer aucune donnée (aucun oracle). Le parcours VALIDE, qui exige le token
# HMAC porté par le QR du PDF, est couvert par PublicQuittanceIntegrationTest et la vérification
# navigateur du Gate Staging (le token n'est pas récupérable via l'API — c'est voulu).
RID="00000000-0000-0000-0000-000000000000"
expect_status 200 "GET /api/public/receipts/{id} sans auth -> 200" \
  "$BASE/api/public/receipts/$RID?token=faux"
echo "$BODY" | jq -e '.resultat=="INVALIDE" and .quittance==null' >/dev/null \
  && ok "réponse publique indifférenciée (INVALIDE, aucune donnée)" \
  || ko "réponse publique inattendue : $(echo "$BODY" | head -c 200)"
expect_status 404 "GET /api/public/receipts/{id}/download token invalide -> 404" \
  "$BASE/api/public/receipts/$RID/download?token=faux"

# =============================================================================
printf '\n\033[1m== Bilan : %d PASS, %d FAIL ==\033[0m\n' "$PASS" "$FAIL"
[[ "$FAIL" == "0" ]]
