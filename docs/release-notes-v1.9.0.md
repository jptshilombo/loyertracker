# Release Notes — LoyerTracker v1.9.0

> Document de release (CGPA v5.4.1, Release Governance). **Candidat validé par le Gate
> Production du 2026-07-06 ; go-live non autorisé à ce stade.** Release
> **unique** couvrant les Sprints 11 (EP-14a) et 12 (EP-14b) : le QR imprimé par le Sprint 11
> n'est vérifiable qu'avec la surface publique du Sprint 12 — les deux sont indissociables en
> Production (ADR-15 K5).

## 1. Identification de la release (D-REL-001)

| Champ | Valeur |
|---|---|
| **Version** | `1.9.0` (Semantic Versioning) |
| **Date du Gate** | 2026-07-06 — date de go-live à renseigner après déploiement effectif |
| **Commit / artefact** | `75646d8ff8ea789b3c67f7977a8852c50cf68119` — images GHCR `loyertracker-{api,web}:sha-75646d8f` |
| **Digests** | API `sha256:3c227910…71aff` ; Web `sha256:f0146fa6…19a04` |
| **Périmètre fonctionnel** | Quittances certifiées, vérifiables et infalsifiables (EP-14) : persistance/numérotation/versions/QR signé (Sprint 11), **vérification publique + observabilité** (Sprint 12) |
| **Environnement cible** | Production (`https://loyertracker.loyerpro.org`) — **non déployée** ; Gate GO, Préflight PASS et autorisation distincte requis |
| **Responsable de validation** | PO `jptshilombo@gmail.com` |

## 2. Contenu

Cf. `CHANGELOG.md` (section [Non publié], blocs EP-14a et EP-14b) pour le détail. En synthèse :

- **Sprint 11 (EP-14a, US-99/100/101)** : quittance devenue **exemplaire officiel stocké**
  (migration V22), numéro permanent par bailleur+année, versions chaînées, `content_hash`/
  `pdf_hash`, token HMAC + QR de vérification, redesign professionnel du PDF A4.
- **Sprint 12 (EP-14b, US-102/103/104)** : **API publique** de vérification/téléchargement
  (contrat K2 strict, réponses indifférenciées, re-hash du PDF avant envoi), **page publique**
  `/verify/receipt/:id` (sans authentification, `noindex`), **observabilité** (métriques
  Prometheus, journal RGPD-minimal, rate-limit nginx).

## 3. Déploiement

- **Source d'images** : GHCR (`ghcr.io/jptshilombo/loyertracker-{api,web}`), **tag immuable
  `sha-<8>`** — jamais `latest`.
- **Production** : `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d` avec
  `LOYERTRACKER_TAG=sha-<8>`.
- **Préflight secret (bloquant)** : `QUITTANCE_HMAC_SECRET` doit être présent dans le `.env`
  hôte **avant** le déploiement (sinon secret éphémère → QR non vérifiables après redémarrage).
  Voir `docs/guides/rotation-secret-hmac-quittances.md`. `QUITTANCE_TOKEN_KID` et
  `QUITTANCE_VERIFY_BASE_URL` confirmés.
- **Nouvelle surface publique** : rate-limit nginx (`limit_req`) actif sur `/api/public/` et
  `/verify/` — un seul `infra/nginx/nginx.conf` canonique (monté en prod, embarqué dans l'image
  web). Vérifier `429` sous rafale au smoke.

## 4. Vérification / recette

- Backend `mvn verify` vert (dont `PublicQuittanceIntegrationTest` : non-fuite K2, échec
  indifférencié, intégrité PDF) ; frontend `ng lint`/`build`/`test` verts.
- Smoke `infra/smoke/smoke-stack.sh` : parcours métier complet + surface publique sans oracle.
- **Vérification navigateur** de la page `/verify/receipt/:id` (QR valide → ✓ Authentique +
  téléchargement ; token trafiqué → ❌), et confirmation qu'aucune route protégée n'est relâchée.
- Gate Staging (dont `STG-ISOL-01`) GO, revue sécurité `/security-review` de la surface publique.

## 5. Réserves / suites

- Gate Production `1.9.0` GO ; déploiement subordonné au **Préflight PASS et à une autorisation distincte** (checklist habituelle : préflight
  backup, digests, déploiement ciblé, smoke prod, hypercare T0/T+12/T+24).
- `RSV-PROD-EP14-02` **levée** : option A exécutée, Angular 22 est inclus dans la release
  unique avec les Sprints 11 et 12.
