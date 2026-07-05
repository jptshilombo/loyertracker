# Plan d'Exécution CGPA — EP-14 : Quittances certifiées, vérifiables et infalsifiables

| Champ | Valeur |
|---|---|
| Date | 2026-07-05 |
| Origine | Instruction PO du 2026-07-05 (« Evolution Enterprise : Quittances certifiées ») |
| Backlog couvert (à créer) | EP-14 — US-99 → US-104 (addendum backlog à produire après validation de ce plan, pattern `addendum-backlog-ep10-ep12.md`) |
| ADR | **ADR-15** (D-QC-001) — architecture, sécurité, RGPD ; **acceptée, K1–K5 tranchés par le PO au kickoff du 2026-07-05** (numérotation par bailleur+année ; page publique = liste complète du besoin ; sans BROUILLON ; domaine `loyertracker.loyerpro.org` ; 2 sprints, release unique) |
| Release cible | **`1.9.0`** — promotion Production **unique** après le Sprint 12 (cf. ADR-15 K5) |
| Prérequis | Release `1.8.0` clôturée (CDO GO 2026-07-05) ✅ — aucun sprint en cours |

## Vue d'ensemble et séquencement

```
Sprint 11 (EP-14a)                      Sprint 12 (EP-14b)
Socle certifié + PDF redesign     →     Vérification publique + observabilité
V22, numéro, version, hash,             API publique, page /verify, téléchargement
token, QR, logo, thème, cachet          officiel, compteurs, rate-limit, tests sécu
        └──────────────── Gate Staging par sprint ────────────────┘
                     Gate Production unique → release 1.9.0
```

**Pourquoi ce découpage** : le QR imprimé sur les PDF pointe vers la page publique — mettre en
Production le Sprint 11 seul produirait des QR morts. Le Sprint 11 est fusionnable sur `main`
et promouvable en **Staging** (validation du rendu), mais la promotion **Production** exige les
deux sprints (ADR-15 K5). Chaque sprint garde son Gate Staging (`STG-ISOL-01` inclus) ; le Gate
Production `1.9.0` reste une décision distincte — aucune confusion clôture sprint / autorisation
Production.

## Sprint 11 — EP-14a : Socle quittance certifiée + redesign PDF

| Champ | Valeur |
|---|---|
| Objectif | Toute quittance émise devient un objet certifié persistant, avec un PDF professionnel portant QR, hash, numéro, version et cachet |
| Stories | **US-99** (persistance, numérotation, versions, statuts, hash — V22), **US-100** (token HMAC + QR + cachet + architecture thème/PAdES-ready), **US-101** (redesign PDF A4 niveau pro, logo LoyerTracker) |
| Livrables | Migration **V22** (`quittance`, `quittance_verification_log`, séquence de numérotation, RLS FORCE + policies, fonction `SECURITY DEFINER` de lecture publique) ; `QuittanceCertifieeService` (émission, ré-émission version N+1, chaînage `remplacee_par`) ; `TokenQuittanceService` (HMAC-SHA256, `RECEIPT_HMAC_SECRET` via `.env`, `token_kid`) ; génération QR (ZXing, data-URI) ; refonte `DocumentHtmlBuilder` (gabarit quittance certifiée : logo, statut, bailleur, locataire, patrimoine, bien, période, tableau des montants, mode de paiement, garantie utilisée le cas échéant, `content_hash`, version, QR + mention de vérification, mentions légales, cachet électronique) ; `ThemeQuittance` injectable (défaut LoyerTracker) ; intégration du logo (`/logo` → ressources backend + assets frontend, renommé proprement) ; extension export RGPD (métadonnées quittances) ; tests unitaires (numérotation, hash canonique, token, HTML) + intégration (émission, ré-émission, RLS cross-tenant) + test PDF (rendu non vide, A4) |
| Hors périmètre | Page publique, API publique (Sprint 12) — le QR est généré mais la cible n'est pas encore servie |
| Dépendances | Kickoff K1/K3/K4 : ✅ tranchés le 2026-07-05 (ADR-15 §Points tranchés) |
| Risques | Séquence de numérotation et concurrence (deux émissions simultanées) — verrou/`nextval` par transaction, test dédié ; canonicalisation du payload de hash instable entre versions de code — format documenté et testé octet à octet ; poids BYTEA dans `pg_dump` — mesuré en Staging |
| Critères GO (fin de sprint) | ✅ `mvn verify`/`ng test` verts sans régression (S01→S04, garanties, RGPD) ✅ avis d'échéance inchangé ✅ contrat HTTP `GET .../quittance` conservé ✅ V22 rollback applicatif seul démontré ✅ CI complète verte ✅ Gate Staging (dont `STG-ISOL-01`) — **pas de promotion Production** |

## Sprint 12 — EP-14b : Vérification publique + observabilité

| Champ | Valeur |
|---|---|
| Objectif | Quiconque scanne le QR obtient une preuve d'authenticité en temps réel et peut télécharger l'exemplaire officiel |
| Stories | **US-102** (API publique `GET /api/public/receipts/{id}` + `/download`, contrat minimal, réponses indifférenciées, vérif `pdf_hash` avant envoi, compteurs), **US-103** (page Angular publique `/verify/receipt/:id` sans `authGuard` : ✓ Authentique / ❌ invalide, métadonnées K2, statut temps réel avec « remplacée par QT-… », bouton téléchargement, `noindex`), **US-104** (observabilité : métriques Prometheus `quittance_verifications_total{resultat}` / `quittance_telechargements_total` / `quittance_qr_invalides_total`, journal RGPD-minimal, rate-limit nginx `/api/public/` + `/verify/`) |
| Livrables | `SecurityConfig` (`permitAll` ciblé — précédent : acceptation d'invitation) ; endpoints publics documentés (OpenAPI) ; composant Angular public + route ; conf nginx `limit_req` (projet **et** overlay staging/prod — invocation Compose canonique par environnement, cf. `staging-state.md` §11) ; tests API (200/❌ indifférencié/429), tests sécurité (token forgé, id inconnu, token d'une autre quittance, version décalée, PDF altéré en base → refus de servir), test e2e de la page publique ; alignement du **compteur Flyway du smoke à 22** (défaut récurrent R-S04-1, à merger avant tout déploiement — précédent PR #171) ; documentation complète (guide utilisateur « vérifier une quittance », guide admin « rotation du secret HMAC », doc développeur, modèle de données, release notes `1.9.0`) |
| Dépendances | Sprint 11 fusionné ; K2 (RGPD champs publics) : ✅ tranché le 2026-07-05 — liste complète du besoin (ADR-15 §Points tranchés) |
| Risques | Exposition publique = nouvelle surface d'attaque — revue sécurité dédiée avant Gate Staging (`/security-review`) ; oubli du rate-limit dans un des deux overlays nginx — checklist Gate Staging ; fuite d'oracle dans les messages d'erreur — testé explicitement |
| Critères GO (fin de sprint) | ✅ tests sécurité 100 % verts ✅ aucune donnée hors contrat K2 dans les réponses publiques (test de non-fuite) ✅ smoke 59+N/0 avec compteur Flyway 22 ✅ CI verte ✅ Gate Staging PASS → **Gate Production `1.9.0`** (décision distincte, checklist habituelle : préflight backup, digests, déploiement ciblé, smoke, hypercare T0/T+12/T+24) |

## Gouvernance transverse

| Artefact | Échéance |
|---|---|
| ADR-15 acceptée (K1–K5 tranchés) | Kickoff Sprint 11 |
| Addendum backlog EP-14 (US-99→104) | Après validation de ce plan, avant Sprint 11 |
| `CHANGELOG.md` `[Non publié]` au fil des sprints | Chaque fusion `main` |
| `docs/project-state.md` / `staging-state.md` / `prod-state.md` | Chaque Gate |
| Diagramme modèle de données (ajout `quittance`) | Sprint 11 |
| Release notes `1.9.0` | Avant Gate Production |
| Rapport final de conformité CGPA EP-14 | Clôture release `1.9.0` |

## Ce que ce plan n'autorise pas

- Aucune promotion Staging/Production sans son Gate (dont `STG-ISOL-01` sur l'hôte mutualisé).
- Aucune mise en Production du Sprint 11 seul (QR sans page de vérification).
- Aucune modification des décisions historiques (arbitrage C reste consigné — ADR-15 D1).
- Aucune certification rétroactive automatique des quittances émises avant EP-14 (ADR-15).
- Aucune collecte d'IP/user-agent bruts sur la page publique (ADR-15, RGPD).
