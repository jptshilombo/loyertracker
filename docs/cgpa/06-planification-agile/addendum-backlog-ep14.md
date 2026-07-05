# Addendum Backlog — Epic EP-14 (Quittances certifiées, vérifiables et infalsifiables)

| Champ | Valeur |
|-------|--------|
| Document de référence | `product-backlog.md`, `addendum-patrimoine-backlog.md`, `addendum-backlog-ep10-ep12.md` — **non modifiés** |
| Statut | **Validé PO** — kickoff du 2026-07-05, arbitrages K1–K5 tranchés (ADR-15 §Points tranchés) |
| Date | 2026-07-05 |
| Décisions liées | ADR-15 (D-QC-001) |
| Plan d'exécution | `plan-execution-ep14-quittances-certifiees.md` (Sprints 11–12, release `1.9.0`) |

> **Numérotation.** US-01→98 sont déjà occupées (EP-01→13). Ce document introduit **US-99 à
> US-104** sous l'epic **EP-14**, sans modifier aucun backlog déjà validé.

---

## EP-14 — Quittances certifiées

| ID | Epic | Jalons | Priorité |
|----|------|--------|----------|
| EP-14 | **Quittances certifiées** — persistance, numérotation permanente, versions, hash, QR signé, vérification publique, observabilité | Post-`1.8.0` (Sprints 11–12) | Must |

### US-99 — Quittance persistée, numérotée, versionnée, hachée (Sprint 11)

**En tant que** bailleur, **je veux** que chaque quittance émise soit un document officiel
persistant avec un numéro permanent, une version et des empreintes cryptographiques **afin de**
disposer d'un exemplaire de référence opposable et infalsifiable.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un loyer intégralement `RECU` **W** le bailleur demande la quittance **T** une ligne `quittance` est créée (V22) avec numéro `QT-YYYY-NNNNNN` (séquence **par bailleur+année**, K1), version 1, statut `EMISE`, `content_hash` (SHA-256 du payload canonique), `pdf_hash` (SHA-256 du PDF stocké), PDF officiel en base — et le PDF est renvoyé avec le contrat HTTP existant inchangé. **G** une quittance déjà émise **W** le bailleur la redemande après correction **T** une version N+1 est émise, l'ancienne passe à `REMPLACEE` avec `remplacee_par` renseigné, l'historique complet est conservé. **G** une quittance annulée ou remplacée **W** on liste les quittances **T** son numéro n'est **jamais** réutilisé. **G** deux émissions concurrentes du même bailleur **W** elles s'exécutent en parallèle **T** deux numéros distincts, aucun doublon (test de concurrence). **G** un autre tenant **W** il tente de lire la quittance **T** RLS bloque (test cross-tenant). |
| Dépendances | Aucune (ADR-15 acceptée) |
| Priorité | Must |
| Points | 8 |
| Risques | Canonicalisation du payload de hash instable ; concurrence sur la séquence ; poids BYTEA dans `pg_dump` |
| Source | Besoin PO §4, §5, §6, §14 ; ADR-15 D1/D2 |

### US-100 — Token signé, QR sécurisé, cachet, architecture extensible (Sprint 11)

**En tant que** détenteur d'une quittance, **je veux** un QR code infalsifiable pointant vers la
vérification officielle **afin de** prouver l'authenticité du document.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** une quittance émise **W** le PDF est généré **T** il porte un QR contenant uniquement `https://loyertracker.loyerpro.org/verify/receipt/{id}?token=…&v=…` (jamais le PDF ni des données personnelles), token = HMAC-SHA256 serveur lié à `id`+`version`+`content_hash`, non stocké en base. **G** un token forgé/tronqué/d'une autre quittance **W** il est vérifié **T** rejet en temps constant. **G** le secret HMAC **W** on inspecte base et dépôt **T** il n'apparaît que dans `.env` (`RECEIPT_HMAC_SECRET`), jamais commité ; `token_kid` permet une rotation sans invalider les QR imprimés. **G** le PDF **W** on le lit **T** il porte le cachet « Document généré électroniquement par LoyerTracker. Toute modification rend ce document invalide. » et la mention « Scanner ce QR Code pour vérifier l'authenticité de cette quittance. ». L'architecture expose `ScellementQuittance` (PAdES branchable ultérieurement) et `ThemeQuittance` (logo/couleurs/signature bailleur futurs) sans modification du moteur PDF. |
| Dépendances | US-99 |
| Priorité | Must |
| Points | 5 |
| Risques | Densité du QR (token court exigé) ; gestion du secret en déploiement (préflight `.env`) |
| Source | Besoin PO §1, §3, §7, §10, §12 ; ADR-15 D3/D4/D6 |

### US-101 — Redesign professionnel du PDF de quittance (Sprint 11)

**En tant que** bailleur, **je veux** une quittance au niveau des standards professionnels
(Stripe, DocuSign) **afin de** remettre un document crédible et lisible à mon locataire.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** une quittance certifiée **W** le PDF est rendu **T** il est A4, porte le logo LoyerTracker (repris de `/logo`, intégré aux ressources du projet), et présente : titre, statut, informations bailleur, locataire, patrimoine, bien, période, tableau des montants (HC/charges/total, devise ADR-13), mode de paiement, garantie utilisée le cas échéant (lien V21), `content_hash`, version, numéro, QR, mentions légales, cachet. **G** l'avis d'échéance **W** il est généré **T** il est inchangé (arbitrage C maintenu pour lui). **G** la suite de tests documents existante **W** elle s'exécute **T** aucune régression. |
| Dépendances | US-99, US-100 |
| Priorité | Must |
| Points | 5 |
| Risques | Rendu OpenHTMLtoPDF (CSS print limité) ; lisibilité du hash long sur A4 |
| Source | Besoin PO §3, §9 ; ADR-15 D6 |

### US-102 — API publique de vérification et de téléchargement (Sprint 12)

**En tant que** tiers vérificateur (locataire, CAF, banque…), **je veux** vérifier une quittance
et télécharger l'exemplaire officiel sans compte **afin de** m'assurer de son authenticité.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un `GET /api/public/receipts/{id}?token=…` avec token valide **T** 200 + contrat public strict (champs K2 uniquement : numéro, bailleur, patrimoine, bien, locataire, période, montant+devise, date d'émission, statut, version, hash — jamais email/mode de paiement détaillé/IDs internes) et `nb_verifications` incrémenté. **G** id inconnu, token invalide, version décalée ou PDF altéré en base **T** réponse ❌ **indifférenciée** (aucun oracle) et compteur d'invalides incrémenté. **G** `GET /api/public/receipts/{id}/download?token=…` valide **T** le PDF stocké est re-haché et servi **seulement si** son SHA-256 == `pdf_hash` ; `nb_telechargements` incrémenté. **G** une quittance `REMPLACEE` **T** la réponse indique le numéro remplaçant. **G** une rafale de requêtes **T** 429 (rate-limit nginx). La lecture publique passe par la fonction `SECURITY DEFINER` dédiée (pattern V10/V12/V13), jamais par une désactivation de RLS. |
| Dépendances | US-99, US-100 ; Sprint 11 fusionné |
| Priorité | Must |
| Points | 8 |
| Risques | Surface publique (revue sécurité dédiée exigée avant Gate Staging) ; oubli du rate-limit dans un overlay nginx |
| Source | Besoin PO §2, §8, §11, §12, §13 ; ADR-15 D5 |

### US-103 — Page publique de vérification (Sprint 12)

**En tant que** personne scannant le QR, **je veux** une page claire affichant ✓ Authentique ou
❌ Quittance invalide **afin de** conclure sans compétence technique.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** le scan d'un QR valide **W** la page `/verify/receipt/{id}` s'ouvre (route Angular **sans** `authGuard`, `noindex`) **T** elle affiche ✓ Authentique, les champs K2, le statut temps réel (`EMISE`/`ANNULEE`/`REMPLACEE` — avec « Cette quittance a été remplacée par : QT-… » le cas échéant) et un bouton « Télécharger le PDF officiel ». **G** un token invalide ou un id inconnu **T** ❌ Quittance invalide, sans détail exploitable. **G** un utilisateur non authentifié **T** aucune redirection Keycloak sur cette route ; les routes existantes restent gardées. |
| Dépendances | US-102 |
| Priorité | Must |
| Points | 5 |
| Risques | Exception au guard global — test explicite de non-régression des routes protégées |
| Source | Besoin PO §2, §8, §11 ; ADR-15 D5 |

### US-104 — Observabilité et anti-fraude (Sprint 12)

**En tant que** PO, **je veux** mesurer vérifications, téléchargements et tentatives invalides
**afin d'** alimenter les statistiques futures et détecter la fraude.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** des vérifications/téléchargements/échecs **W** Prometheus scrape l'API **T** `quittance_verifications_total{resultat}`, `quittance_telechargements_total`, `quittance_qr_invalides_total` progressent. **G** chaque événement de vérification **T** une ligne `quittance_verification_log` (horodatage, quittance_id nullable, résultat) **sans IP ni user-agent bruts** (ADR-03/ADR-15 RGPD). **G** les compteurs par quittance **T** exposés au bailleur authentifié (ses quittances uniquement). |
| Dépendances | US-102 |
| Priorité | Should |
| Points | 3 |
| Risques | Tentation de sur-collecte (« origine des scans ») — cadré par ADR-15 : agrégats anonymes uniquement |
| Source | Besoin PO §15 ; ADR-15 D7 |
