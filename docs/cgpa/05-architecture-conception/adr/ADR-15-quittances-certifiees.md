# ADR-15 — Quittances certifiées, vérifiables et infalsifiables (EP-14)

| Champ | Valeur |
|---|---|
| Code de décision | **D-QC-001** |
| Statut | **Acceptée** — kickoff du 2026-07-05, points K1–K5 tranchés par le PO (voir §Points tranchés) ; Plan d'Exécution `plan-execution-ep14-quittances-certifiees.md` |
| Date | 2026-07-05 |
| Phase | 07 — Développement (lot post-`1.8.0`, continu) |
| Documents liés | `plan-execution-ep14-quittances-certifiees.md`, ADR-01 (RLS multitenant), ADR-03 (RGPD by design), ADR-13 (Money), rapport S03 (arbitrage C — documents à la volée) |

## Contexte

Depuis le Sprint 3, les documents locatifs (quittance de loyer, avis d'échéance) sont générés
**à la volée** et jamais stockés — c'est l'**arbitrage C**, consigné au rapport d'exécution S03
et inscrit dans le code (`QuittanceService`, `PdfRenderer`). Ce choix était adapté à des
documents informatifs : zéro stockage, zéro cycle de vie, zéro exposition.

L'évolution EP-14 (PO, 2026-07-05) demande des quittances **certifiées** : numéro unique
permanent, versions, hash, QR code signé, page publique de vérification, téléchargement du PDF
officiel, compteurs d'usage. Ces exigences sont **incompatibles avec la génération à la volée**
: un document vérifiable doit être un objet persistant, immuable et identifiable.

## Problème

1. Comment rendre une quittance **infalsifiable et vérifiable publiquement** sans exposer les
   données des tenants (ADR-01) ni violer le RGPD (ADR-03) ?
2. Comment introduire la persistance des quittances **sans réécrire l'arbitrage C historique**
   ni casser les endpoints existants ?
3. Comment afficher un hash **sur** le PDF alors que le hash d'un PDF ne peut pas couvrir un
   document qui le contient (dépendance circulaire) ?

## Décision

### D1 — Renversement partiel et tracé de l'arbitrage C

Les **quittances certifiées sont persistées** (nouvelle table `quittance`, migration **V22**).
L'arbitrage C **n'est pas réécrit** : il reste vrai pour les avis d'échéance (toujours à la
volée) et il est historiquement exact pour les quittances émises avant EP-14. Le présent ADR
le **supplante pour les quittances à compter de l'EP-14**, avec cette justification : la
certification exige l'immuabilité d'un exemplaire de référence.

### D2 — Modèle de données (V22)

Table `quittance`, RLS `FORCE` par `bailleur_id` (pattern ADR-01, chaque table métier porte sa
colonne) :

| Colonne | Type | Rôle |
|---|---|---|
| `id` | UUID PK | identifiant public (URL de vérification) — non séquentiel, non devinable |
| `bailleur_id` | UUID NOT NULL | RLS |
| `paiement_id` | UUID FK NOT NULL | loyer quittancé |
| `numero` | VARCHAR NOT NULL | `QT-YYYY-NNNNNN` — permanent, jamais réutilisé (séquence dédiée, voir kickoff K1) |
| `version` | INT NOT NULL DEFAULT 1 | incrémentée à chaque régénération ; historique conservé (une ligne par version) |
| `statut` | VARCHAR NOT NULL | `EMISE` / `ANNULEE` / `REMPLACEE` (voir kickoff K4 pour `BROUILLON`) |
| `remplacee_par` | UUID FK NULL | chaînage de remplacement |
| `content_hash` | CHAR(64) NOT NULL | SHA-256 du **payload canonique** (données métier sérialisées de façon déterministe) — imprimé sur le PDF, lié au token |
| `pdf_hash` | CHAR(64) NOT NULL | SHA-256 des **octets du PDF stocké** — intégrité du fichier téléchargé |
| `pdf` | BYTEA NOT NULL | exemplaire officiel unique |
| `token_kid` | SMALLINT NOT NULL DEFAULT 1 | identifiant de clé HMAC (rotation future) |
| `emise_le` | TIMESTAMPTZ NOT NULL | date d'émission |
| `nb_telechargements` / `nb_verifications` | INT DEFAULT 0 | compteurs d'usage |

**Résolution de la circularité du hash (problème 3)** : le hash **imprimé sur le PDF** et lié
au token est `content_hash` (calculé sur les données, avant rendu) ; `pdf_hash` (calculé après
rendu) n'apparaît que sur la page de vérification et sert à prouver que le PDF téléchargé est
l'exemplaire officiel intact. Un suffixe court de `content_hash` figure aussi dans le nom
lisible sous le QR.

### D3 — Token signé HMAC (pas de JWT)

`token = base64url(HMAC-SHA256(secret, id || "." || version || "." || content_hash))`.

- **HMAC plutôt que JWT** : aucun claim à transporter, pas de lib supplémentaire côté
  vérification, token court (QR moins dense), pas de risque d'algorithme `none`/confusion RS/HS.
- **Non expirant, lié au contenu** : une quittance papier doit rester vérifiable des années
  après impression. La révocation ne passe pas par l'expiration mais par le **statut en base**
  (`ANNULEE`/`REMPLACEE` affiché lors de la vérification). Le token n'est **pas stocké** en
  base (dérivé à la demande) : une fuite de la base ne donne aucun token — seul le secret
  serveur (`RECEIPT_HMAC_SECRET`, `.env`, jamais commité) permet d'en fabriquer.
- `token_kid` permet une rotation de secret sans invalider les QR déjà imprimés (les anciens
  kid restent vérifiables en lecture).
- Comparaison en temps constant (`MessageDigest.isEqual`) — pas d'oracle de timing.

### D4 — QR code

Contenu du QR : `https://loyertracker.loyerpro.org/verify/receipt/{id}?token={token}&v={version}`
— uniquement l'URL de vérification (jamais le PDF, jamais de données personnelles). Génération
serveur via **ZXing** (`com.google.zxing:core` + `javase`), image PNG embarquée en data-URI
dans le XHTML (OpenHTMLtoPDF, aucune ressource externe).

### D5 — Vérification publique

- **API** : `GET /api/public/receipts/{id}?token=…` (métadonnées strictement nécessaires) et
  `GET /api/public/receipts/{id}/download?token=…` (PDF officiel stocké). `permitAll` dans
  `SecurityConfig` (précédent existant : acceptation d'invitation).
- **RLS** : la lecture publique n'a pas de contexte tenant. Réutilisation du **pattern établi**
  du projet : fonction SQL `SECURITY DEFINER` (owner `loyertracker_batch` BYPASSRLS, exécutée
  par `loyertracker_api` — même mécanisme que V10/V12/V13), lecture seule, **ne renvoyant que
  les colonnes du contrat public** et uniquement si le token fourni est valide (le HMAC est
  vérifié en Java avant l'appel ; la fonction reçoit l'id déjà authentifié par capability).
- **Frontend** : route Angular publique `/verify/receipt/:id` **sans** `authGuard` (les routes
  actuelles sont toutes gardées ; la route publique est une exception explicite), affichant
  ✓ Authentique / ❌ Quittance invalide, les métadonnées, le statut temps réel (dont « remplacée
  par QT-… »), et le bouton de téléchargement officiel. `<meta name="robots" content="noindex">`.
- **Anti-abus** : `429` par `limit_req` nginx sur `/api/public/` et `/verify/` (zone dédiée) ;
  réponses d'échec **indifférenciées** (id inconnu, token invalide, hash divergent → même
  ❌ générique, pas d'oracle) ; journalisation des échecs (D7).

### D6 — Rendu PDF, cachet et extensibilité

- Redesign complet du gabarit quittance dans `DocumentHtmlBuilder` (A4, logo LoyerTracker
  repris de `/logo` et intégré aux ressources du projet, sections : en-tête/statut, bailleur,
  locataire, patrimoine/bien, période, tableau des montants, mode de paiement, garantie
  utilisée le cas échéant, hash, version, QR + mention « Scanner ce QR Code pour vérifier
  l'authenticité de cette quittance. », mentions légales et cachet « Document généré
  électroniquement par LoyerTracker. Toute modification rend ce document invalide. »).
- **Thème de rendu injectable** (`ThemeQuittance` : logo, couleurs, signature/tampon image) —
  résolu par bailleur avec défaut LoyerTracker. La personnalisation future (logo/couleurs/
  signature du bailleur, §10 du besoin) ne touchera **que** le fournisseur de thème, pas le
  moteur PDF.
- **PAdES-ready** : interface `ScellementQuittance` avec implémentation actuelle « cachet
  visuel + hash » ; une implémentation PAdES (signature cryptographique PDFBox — déjà dans le
  classpath via OpenHTMLtoPDF) pourra être branchée sans changer le flux d'émission. Le PDF
  stocké étant l'exemplaire de référence, une re-signature émettrait une **nouvelle version**
  (jamais de mutation en place).

### D7 — Observabilité

- Compteurs Micrometer/Prometheus : `quittance_verifications_total{resultat}`,
  `quittance_telechargements_total`, `quittance_qr_invalides_total` (alimenteront les
  statistiques futures et la détection de tentatives de fraude).
- Compteurs persistés par quittance (`nb_verifications`, `nb_telechargements`).
- Journal `quittance_verification_log` (horodatage, quittance_id nullable, résultat) — **sans
  IP ni user-agent bruts** (voir Impacts RGPD).

## Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| JWT signé dans le QR | Claims inutiles (tout est en base), token plus long (QR plus dense), surface d'attaque JWT (confusion d'algorithme) ; HMAC dédié plus simple et plus sûr ici |
| Token à expiration + renouvellement | Une quittance papier doit rester vérifiable durablement ; l'état d'invalidité est porté par le **statut en base**, pas par le temps |
| Stockage des PDF sur le filesystem ou S3 | Hôte unique Docker Compose sans objet store ; BYTEA en base met le PDF sous RLS, sous transaction et **sous le backup pg_dump existant** (préflight/restore inchangés) ; volumétrie faible (~50–100 Ko/quittance, quelques dizaines/mois) |
| Hash unique du PDF affiché sur le PDF | Impossible (circularité) ; résolu par le couple `content_hash` (imprimé) / `pdf_hash` (page de vérification) |
| Rendu du QR côté navigateur (lib JS) | Le QR doit être **dans** le PDF officiel généré côté serveur ; un rendu client ne serait pas l'exemplaire certifié |
| Certification rétroactive automatique des quittances déjà émises | Aucun exemplaire de référence n'existe (arbitrage C) ; une quittance passée pourra être **ré-émise** certifiée (version 1 de l'objet certifié) à la demande, jamais rétro-certifiée d'office |

## Conséquences

- `GET /api/biens/{bienId}/paiements/{periode}/quittance` **change de sémantique** : il émet
  (ou ré-émet en version N+1) une quittance certifiée persistée puis renvoie le PDF officiel —
  la signature HTTP et le contrat frontend existants sont conservés (compatibilité ascendante).
  L'avis d'échéance est inchangé (à la volée).
- Nouveau composant `QuittanceCertifieeService` distinct de l'assemblage existant ; `DonneesDocument`
  s'enrichit (numéro, version, hash, QR, mode de paiement, garantie, patrimoine, locataire).
- Le backup Production couvre désormais les exemplaires officiels (aucun changement d'outillage).
- L'export RGPD du bailleur doit inclure les métadonnées de ses quittances certifiées.

## Impacts sécurité

Menaces traitées : QR falsifié (HMAC serveur), URL modifiée (le token lie `id`+`version`+
`content_hash`), ID deviné (UUID + token requis, réponses indifférenciées), token expiré (sans
objet — révocation par statut), hash incorrect (double hash, vérif à chaque téléchargement :
le PDF servi est re-hashé et comparé à `pdf_hash` avant envoi), document modifié (tout écart →
❌), énumération/scraping (rate-limit nginx + compteurs d'invalides + journal), fuite de base
(token non stocké, secret hors base). Tests de sécurité dédiés exigés (plan §tests).

## Impacts RGPD (ADR-03)

- La page publique expose des données personnelles (noms, adresse du bien, montant) **à
  quiconque détient le token** — c'est-à-dire, par construction, le détenteur de la quittance
  papier/PDF qui porte déjà ces informations. Le token est une *capability* : pas de token, pas
  de données. Périmètre exact des champs affichés : **décision PO au kickoff (K2)**.
- Minimisation : l'API publique ne renvoie que le contrat de la page (jamais l'email, jamais
  l'IBAN/mode de paiement détaillé, jamais d'identifiants internes autres que `id`/`numero`).
- Journal des vérifications **sans IP brute ni user-agent brut** ; « origine des scans » est
  approximée par des agrégats anonymes (compteurs par résultat/jour). Toute exploitation plus
  fine exigerait une base légale — hors périmètre EP-14.
- Droit à l'effacement : la suppression logique d'un locataire/bail n'efface pas la quittance
  certifiée (obligation de conservation comptable du bailleur) — le numéro n'est **jamais**
  réutilisé ; l'anonymisation éventuelle est un sujet distinct, consigné au backlog.

## Impacts performances

Génération : +1 hash SHA-256 + 1 QR (~ms) par émission — négligeable. Stockage : BYTEA ~50–100
Ko/version, volumétrie faible ; `pg_dump` grossit proportionnellement (surveillé au préflight,
seuil existant). Vérification publique : 1 SELECT indexé par PK + HMAC — sans impact sur les
parcours authentifiés. Rate-limit nginx en amont de l'API publique.

## Compatibilité et migration

- **V22 additive** : nouvelles tables/séquence uniquement, aucune colonne existante modifiée —
  **rollback applicatif seul viable** (même profil que V21, contraste V20/RSV-S9-03).
- Aucun changement de contrat pour les endpoints authentifiés existants ni pour l'avis
  d'échéance ; le smoke existant (59 tests) doit rester vert, le compteur Flyway du smoke passe
  à **22** (défaut récurrent R-S04-1 : à aligner **avant** le déploiement, précédent PR #171).
- Frontend : ajout d'une route publique ; aucune modification des routes gardées.

## Points tranchés au kickoff (PO, 2026-07-05) — aucune décision implicite

| # | Question | Décision PO |
|---|---|---|
| K1 | Numérotation `QT-YYYY-NNNNNN` par bailleur+année ou globale ? | ✅ **Par bailleur+année** — numérotation métier propre à chaque bailleur, pas de fuite de volumétrie inter-tenants ; l'unicité publique est portée par l'UUID |
| K2 | Champs affichés sur la page publique (liste §2 du besoin vs minimisation) | ✅ **Liste complète du besoin** : numéro, bailleur, patrimoine, bien, locataire, période, montant+devise, date d'émission, statut, version, hash — soit exactement ce que porte la quittance papier détenue par le scanneur. Exposition des noms **explicitement confirmée** au regard de l'ADR-03 (le token est la capability ; jamais d'email, de mode de paiement détaillé ni d'identifiants internes) |
| K3 | Statut `BROUILLON` | ✅ **Écarté** : le flux n'émet une quittance que pour un loyer `RECU` ; statuts retenus `EMISE`/`ANNULEE`/`REMPLACEE`. À réintroduire seulement si un flux de préparation apparaît |
| K4 | Domaine de vérification | ✅ `https://loyertracker.loyerpro.org/verify/...` (domaine réellement détenu — l'exemple `loyertracker.app` du besoin n'est pas notre domaine) |
| K5 | Découpage et release | ✅ **2 sprints** (11 = socle certifié + PDF ; 12 = vérification publique + observabilité), **release Production unique `1.9.0`** — le QR imprimé au Sprint 11 ne va en Production qu'avec la page de vérification opérationnelle |
