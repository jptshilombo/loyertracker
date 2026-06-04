# Expression du besoin (EB) — LoyerTracker

| Champ | Valeur |
|-------|--------|
| Projet | LoyerTracker |
| Auteur | jptshilombo@gmail.com |
| Date | 2026-06-04 |
| Phase | 02 — Expression du besoin |
| Gate visé | Gate 1 (verrou de codage) |
| Statut | ✅ **Validé — Gate 1 Go** (v1.2 — sémantique terme échu précisée) |
| Prérequis | Fiche idée v3 validée — Gate 0 Go (15/20) |

### Changelog v1.1 → v1.2

| Ref | Modification |
|-----|-------------|
| BF-33 / A.3 | **Sémantique « terme échu » précisée** : la `periode` d'une échéance est le **mois consommé** (à partir du mois de **début** du bail) ; elle devient **exigible le 1er du mois suivant** (`date_exigibilite`). Lève l'ambiguïté de la formulation v1.1 « mois suivant le début » (qui décrivait le *paiement*, pas le mois facturé). Cohérent avec le DAT Phase 05 (ADR-04) et le Gate 4. |

### Changelog v1.0 → v1.1

| Ref | Modification |
|-----|-------------|
| §2.2 | Révision IRL ajoutée aux exclusions MVP (Won't) |
| BF-33 | Règles de calcul des loyers attendus précisées (mois plein, charges CC) |
| BF-63 | Alerte révision IRL **supprimée** |
| §4.1 | Flux d'invitation du gestionnaire spécifié (BF-02/03) |
| §4.3 | Cycle de vie du gestionnaire après révocation précisé (BF-22/24) |
| §4.4 | Règles de calcul loyers attendus ajoutées (BF-33) |
| §4.5 | Flux de restitution de garantie modélisé, statuts définis (BF-41/42) |
| §4.6 | Honoraires : calcul automatique uniquement, saisie libre supprimée (BF-51) |
| §4.7 | Moteur d'alertes spécifié : batch quotidien, modèle de données défini |
| §5 | BNF-05 : granularité de l'audit log précisée |
| §A | Annexe — Modèles de données des décisions de cadrage ajoutée |

---

## 1. Contexte & objectifs métier

LoyerTracker répond au besoin d'un **bailleur propriétaire** qui souhaite centraliser la gestion de ses biens locatifs et **déléguer** l'exploitation de tout ou partie de ses biens à un ou plusieurs **gestionnaires mandataires de confiance**, sans perdre la visibilité ni la propriété de ses données.

**Objectifs métier :**
1. **Fiabiliser** le suivi des loyers (plus aucun encaissement non tracé, plus d'oubli d'échéance).
2. **Maîtriser la délégation** : le bailleur affecte des gestionnaires bien par bien, avec un cloisonnement strict des accès.
3. **Sécuriser la rotation** des gestionnaires sans perte d'historique.
4. **Tracer les honoraires** dus à chaque gestionnaire.
5. **Garder le contrôle des données** (souveraineté, RGPD, self-hosting possible).

---

## 2. Périmètre

### 2.1 Inclus (MVP)

- Gestion des comptes **Bailleur** et **Gestionnaire** (invitation tokenisée par le bailleur).
- Gestion des **biens** (CRUD, statut : libre / loué / en travaux).
- Gestion des **baux** (1 bail actif par bien : locataire, loyer CC, dates, dépôt de garantie).
- **Affectation** d'un gestionnaire à un bien (entité pivot), avec honoraires et statut.
- **Rotation** de gestionnaire (révocation + nouvelle affectation) avec conservation de l'historique.
- **Suivi des paiements** de loyer mois par mois (statut : reçu / partiel / en retard / impayé).
- **Suivi des garanties locatives** (dépôt : montant, type, statut jusqu'à restitution totale ou partielle).
- **Calcul automatique des honoraires** du gestionnaire par mois (POURCENTAGE ou FORFAIT) depuis l'Affectation.
- **Alertes d'échéances** : loyer en retard, fin de bail, préavis, garantie non restituée (in-app).
- **Tableaux de bord** différenciés : bailleur (tous biens) / gestionnaire (biens affectés actifs).
- **Cloisonnement des accès** (RBAC) : un gestionnaire ne voit que ses affectations actives ; aucun accès cross-bailleur.
- **Audit log** : journalisation horodatée des actions d'écriture sensibles.

### 2.2 Exclus (hors MVP — roadmap)

| Élément | Décision | Justification |
|---------|----------|---------------|
| Génération de **quittance de loyer PDF** | Should (post-MVP, lot 2) | Forte valeur mais non bloquant pour le cœur « suivi + alertes ». |
| **Mandat de gestion PDF** généré depuis l'Affectation | Could (post-MVP) | Confort, non critique au MVP. |
| **Révision de loyer IRL** (Indice de Référence des Loyers) | Won't (MVP) | Logique réglementaire complexe ; reportée à une version ultérieure. |
| **Rapprochement bancaire** automatique | Won't (MVP) | Complexité + coût d'intégration ; saisie manuelle au MVP. |
| **Aide à la déclaration des revenus fonciers** | Won't (MVP) | Fonctionnalité comptable avancée, hors cœur. |
| **Application mobile native** | Won't (MVP) | Web responsive suffit au MVP. |
| **Paiement en ligne / encaissement** intégré | Won't (MVP) | Implications réglementaires (DSP2), hors périmètre. |
| **Multi-bailleur sur un même compte / SCI multi-associés** | Won't (MVP) | 1 compte = 1 bailleur propriétaire au MVP. |
| **Alertes e-mail** | Won't (MVP) | In-app uniquement ; infrastructure SMTP reportée. |
| **Frais exceptionnels** du gestionnaire | Won't (MVP) | Calcul automatique depuis l'Affectation suffit au MVP. |

> ⚠️ **Risque de scope creep** : quittance PDF, mandat de gestion et révision IRL sont les extensions les plus susceptibles d'être demandées. Le cadrage strict du MVP est une exigence du Gate 1.

---

## 3. Parties prenantes

| Partie prenante | Rôle | Attente |
|-----------------|------|---------|
| **Bailleur propriétaire** | Sponsor / utilisateur principal / décideur Gate | Visibilité consolidée, délégation maîtrisée, propriété des données |
| **Gestionnaire mandataire** | Utilisateur délégué (invité) | Espace de travail clair, limité à ses biens affectés |
| **Locataire** | Sujet de données (non-utilisateur au MVP) | Confidentialité de ses données personnelles (RGPD) |
| **Développeur solo / DevSecOps** | Réalisation, exploitation | Stack maîtrisée, MVP cadré, sécurité by design |
| **Agent IA (Claude Code)** | Copilote CGPA | Livrables traçables, gates respectés |


---

## 4. Besoins fonctionnels

> Priorisation **MoSCoW** : Must / Should / Could / Won't (au MVP).

### 4.1 Comptes, rôles & délégation

| ID | Besoin | Priorité |
|----|--------|----------|
| BF-01 | Le bailleur peut créer son compte et s'authentifier via Keycloak. | Must |
| BF-02 | Le bailleur peut **inviter** un gestionnaire par e-mail. Le système génère un lien tokenisé (UUID v4, usage unique, validité 72h). Si le token est expiré, le bailleur génère un nouveau lien manuellement. | Must |
| BF-03 | Le gestionnaire crée son compte Keycloak **uniquement via le lien d'invitation**. Un compte gestionnaire peut être associé à plusieurs bailleurs — le cloisonnement est assuré par les `Affectation` actives, pas par le compte. | Must |
| BF-04 | Le système attribue les rôles **Bailleur** / **Gestionnaire** et applique les permissions associées via RBAC Keycloak. | Must |

### 4.2 Biens & baux

| ID | Besoin | Priorité |
|----|--------|----------|
| BF-10 | Le bailleur peut créer / modifier / archiver un **bien** (caractéristiques, statut : libre / loué / en travaux). | Must |
| BF-11 | Le bailleur (ou le gestionnaire affecté actif) peut enregistrer un **bail** : locataire, loyer CC (charges comprises), dates début/fin, dépôt de garantie. | Must |
| BF-12 | Le système garantit **un seul bail actif par bien** à la fois. | Must |
| BF-13 | Le bailleur peut consulter l'historique des baux d'un bien. | Should |

### 4.3 Affectation & rotation

| ID | Besoin | Priorité |
|----|--------|----------|
| BF-20 | Le bailleur peut créer une **Affectation** (gestionnaire + bien + type d'honoraires + montant + dates). | Must |
| BF-21 | Le système garantit **un seul gestionnaire ACTIF par bien** à la fois (contrainte d'unicité sur `bienId` + statut `ACTIVE`). | Must |
| BF-22 | Le bailleur peut **révoquer** une affectation active. La révocation passe le statut à `REVOQUEE` et enregistre `dateRevocation`. Le compte Keycloak du gestionnaire n'est **pas** désactivé automatiquement. | Must |
| BF-23 | Après révocation, le bailleur peut créer une nouvelle affectation sur le même bien (**rotation**) sans perte d'historique. | Must |
| BF-24 | Le bailleur peut consulter l'historique complet des affectations d'un bien. Le gestionnaire peut consulter la liste de ses propres affectations passées (dates, bien, statut) **sans accès aux données métier du bien** (loyers, garanties, etc.) après révocation. | Should |

### 4.4 Suivi des paiements

| ID | Besoin | Priorité |
|----|--------|----------|
| BF-30 | Pointer un loyer mois par mois avec un statut : **reçu / partiel / en retard / impayé**. | Must |
| BF-31 | Visualiser l'**historique des paiements** par bien et par période. | Must |
| BF-32 | Enregistrer un paiement **partiel** (montant reçu + reste dû calculé). | Should |
| BF-33 | Le système génère automatiquement les **loyers attendus** par mois (paiement **à terme échu**) selon les règles suivantes : (1) Une échéance porte sur un **mois consommé** (`periode`), depuis le **mois de début** du bail jusqu'au mois du terme — pas de prorata. (2) Chaque échéance de période `m` est **exigible le 1er du mois `m+1`** (`date_exigibilite`) : le locataire paie le mois qu'il a occupé. (3) Le montant attendu est le **loyer CC** (charges comprises) défini dans le bail. (4) Ces règles sont des partis pris MVP ; la révision de loyer (IRL) est hors périmètre. | Must |

### 4.5 Garanties locatives

| ID | Besoin | Priorité |
|----|--------|----------|
| BF-40 | Enregistrer un **dépôt de garantie** : montant, type (caution, garant, Visale, autre), date de versement. | Must |
| BF-41 | Suivre l'**état** de la garantie avec trois statuts : `DETENU → RESTITUE_PARTIEL → RESTITUE_TOTAL`. La restitution peut être initiée par le bailleur ou le gestionnaire affecté actif. | Must |
| BF-42 | Enregistrer une **retenue partielle** à la restitution : montant retenu + motif (champ texte libre). Aucun document généré au MVP. En cas de litige, le bailleur maintient le statut `DETENU` et utilise le champ commentaire. | Should |

### 4.6 Honoraires

| ID | Besoin | Priorité |
|----|--------|----------|
| BF-50 | Définir les honoraires d'une affectation : type (**POURCENTAGE / FORFAIT**) + montant. | Must |
| BF-51 | Le système **calcule automatiquement** les honoraires dus chaque mois selon la règle : `POURCENTAGE` → `montantHonoraires% × loyer_encaissé_du_mois` ; `FORFAIT` → `montantHonoraires` fixe indépendant du paiement. Le gestionnaire consulte le calcul, il ne le saisit pas. Il n'y a pas de saisie libre de frais au MVP. | Should |
| BF-52 | Le bailleur suit et valide le statut des honoraires par période : **DU → EN_ATTENTE → PAYE**. | Should |

### 4.7 Alertes & échéances

| ID | Besoin | Priorité |
|----|--------|----------|
| BF-60 | Alerter sur un **loyer en retard** (au-delà de la date d'échéance + tolérance paramétrable). | Must |
| BF-61 | Alerter sur une **fin de bail** approchante (J-X paramétrable, défaut : J-60). | Must |
| BF-62 | Alerter sur une **échéance de préavis**. | Must |
| BF-63 | Alerter sur une **garantie non restituée** au-delà de X jours après la fin du bail (paramétrable, défaut : 30 jours). Cette alerte est rattachée à BF-61 (fin de bail) et déclenchée par le même job. | Should |
| BF-64 | Le destinataire de l'alerte respecte le **cloisonnement** : bailleur reçoit les alertes de tous ses biens ; gestionnaire reçoit uniquement les alertes des biens affectés actifs. | Must |
| BF-65 | Les alertes sont **in-app uniquement** au MVP (notification visible dans le tableau de bord). | Must |

> **Moteur d'alertes — décision de cadrage :**
> Les alertes sont générées par un **job batch planifié une fois par jour** (ex. 7h00). L'event-driven est hors MVP.
> **Anti-doublon :** une alerte n'est pas régénérée si une alerte du même `type` + `bienId` + `période` existe déjà au statut `NON_LUE`.
> Le modèle de données de l'entité `Alerte` est détaillé en Annexe §A.

### 4.8 Tableaux de bord & cloisonnement

| ID | Besoin | Priorité |
|----|--------|----------|
| BF-70 | **Tableau de bord Bailleur** : vue consolidée tous biens (encaissements du mois, retards, prochaines échéances, alertes non lues). | Must |
| BF-71 | **Tableau de bord Gestionnaire** : limité aux biens affectés actifs. | Must |
| BF-72 | Le système **interdit tout accès** d'un gestionnaire à un bien non affecté ou à un autre bailleur. Contrôle effectué côté serveur, jamais seulement côté UI. | Must |
| BF-73 | Le système **journalise** les actions d'écriture sensibles (voir §5 BNF-05 et Annexe §A). | Should |


---

## 5. Besoins non fonctionnels

| ID | Catégorie | Exigence |
|----|-----------|----------|
| BNF-01 | **Sécurité — Auth** | Authentification centralisée via **Keycloak / OIDC** ; rôles Bailleur/Gestionnaire en RBAC. |
| BNF-02 | **Sécurité — Cloisonnement** | Toute requête de données est **scoped par `bailleurId`** et, pour un gestionnaire, restreinte à ses affectations **actives**. Aucun accès cross-bailleur possible. Contrôle obligatoirement côté serveur (service layer), jamais seulement côté UI. |
| BNF-03 | **Sécurité — Moindre privilège** | Permissions minimales par rôle (cf. matrice §6) ; secrets gérés hors dépôt (variables d'environnement / vault). |
| BNF-04 | **RGPD** | Données locataires/bailleurs minimisées ; base légale documentée ; droits d'accès/rectification/effacement ; durée de conservation définie ; registre de traitement tenu à jour. |
| BNF-05 | **Traçabilité / Audit** | Journalisation horodatée des **actions d'écriture** uniquement (les lectures ne sont pas journalisées au MVP). Actions couvertes : `CREATE_AFFECTATION`, `REVOKE_AFFECTATION`, `CREATE_BAIL`, `UPDATE_BAIL`, `POINTER_PAIEMENT`, `UPDATE_PAIEMENT`, `CREATE_GARANTIE`, `UPDATE_GARANTIE`, `RESTITUER_GARANTIE`, `VALIDER_HONORAIRES`. Stockage dans la table `audit_log` PostgreSQL (même base). Durée de conservation alignée sur la durée du bail + 3 ans (définie dans le registre RGPD). Accès : bailleur uniquement sur son périmètre. |
| BNF-06 | **Performance** | Affichage d'un tableau de bord < 2 s pour un portefeuille jusqu'à ~50 biens. |
| BNF-07 | **Disponibilité** | Cible MVP : best-effort (self-hosted) ; sauvegardes régulières de la base PostgreSQL ; pas d'engagement SLA au MVP. |
| BNF-08 | **Fiabilité des alertes** | Une alerte due est émise au plus tard à J+1 de la condition déclenchante (job batch quotidien). Pas de doublon sur même `type` + `bienId` + `période` tant que l'alerte est `NON_LUE`. |
| BNF-09 | **Portabilité** | Déploiement conteneurisé (**Docker**), self-hosting possible ; configuration externalisée (variables d'environnement). |
| BNF-10 | **Maintenabilité / Qualité** | Tests unitaires + intégration ; Définition de « Terminé » (tests + doc + sécurité) respectée. |
| BNF-11 | **Compatibilité / Accès** | Web **responsive** (consultation mobile des alertes et du tableau de bord). |
| BNF-12 | **Internationalisation / Devise** | Langue **FR**, devise **EUR**, fuseau Europe/Paris au MVP. |
| BNF-13 | **Sauvegarde / Réversibilité** | Export des données du bailleur (portabilité RGPD) ; rollback de déploiement prévu (phases ultérieures). |

---

## 6. Rôles & permissions (matrice de référence)

| Action | Bailleur | Gestionnaire |
|--------|----------|-------------|
| Créer son compte | ✅ | ✅ (sur invitation uniquement) |
| Enregistrer / modifier un bien | ✅ | ❌ |
| Enregistrer / modifier un bail | ✅ | ✅ (biens affectés actifs uniquement) |
| Pointer un paiement | ✅ | ✅ (biens affectés actifs uniquement) |
| Suivre / mettre à jour une garantie | ✅ | ✅ (biens affectés actifs uniquement) |
| Inviter un gestionnaire | ✅ | ❌ |
| Créer une affectation | ✅ | ❌ |
| Révoquer une affectation | ✅ | ❌ |
| Voir les biens non affectés | ✅ | ❌ |
| Voir les biens d'un autre bailleur | ❌ | ❌ |
| Consulter le calcul des honoraires | ✅ | ✅ (lecture seule) |
| Valider le statut des honoraires (DU→PAYE) | ✅ | ❌ |
| Tableau de bord complet (tous biens) | ✅ | ❌ |
| Tableau de bord limité (biens affectés actifs) | ❌ | ✅ |
| Recevoir des alertes | ✅ (tous biens) | ✅ (biens affectés actifs uniquement) |
| Consulter l'historique complet des affectations d'un bien | ✅ | ❌ |
| Consulter ses propres affectations passées (vue Affectation seule) | — | ✅ |
| Consulter l'audit log | ✅ (son périmètre) | ❌ |

---

## 7. Critères de succès (KPI)

| KPI | Cible MVP |
|-----|-----------|
| **Couverture du suivi** : part des loyers attendus ayant un statut renseigné dans le mois | ≥ 95 % |
| **Sécurité du cloisonnement** : accès non autorisés cross-bailleur / cross-affectation détectés | **0** (contrôle bloquant) |
| **Fiabilité des alertes** : échéances critiques (retard, fin de bail, préavis) signalées à temps | ≥ 99 % |
| **Intégrité de la rotation** : rotations de gestionnaire sans perte d'historique | 100 % |
| **Délai de saisie** d'un pointage de paiement | < 30 s |
| **Adoption** : le porteur cesse d'utiliser son tableur pour le suivi | Oui/Non (binaire, à 1 mois) |

---

## 8. Contraintes

- **Stack de référence :** Spring Boot · Angular · Keycloak (OIDC/RBAC) · PostgreSQL · Docker · CI/CD.
- **Ressources :** développeur solo, projet personnel → MVP à périmètre maîtrisé.
- **Conformité :** RGPD obligatoire dès la conception (Shift-Left).
- **Budget :** minimal (auto-hébergement, coûts d'infra réduits).
- **Délais :** non contraints par une date externe ; cadence Agile par sprints.

---

## 9. Hypothèses & dépendances

**Hypothèses (à valider) :**
- [ ] Le bailleur cible engage réellement **plusieurs gestionnaires** sur des biens différents.
- [ ] La **saisie manuelle** des paiements est acceptable au MVP (pas de rapprochement bancaire).
- [ ] La **quittance PDF** peut rester hors MVP (lot 2) sans bloquer l'adoption.
- [ ] Le **mandat de gestion PDF** est un confort post-MVP.
- [ ] La structure d'honoraires se limite à **% ou forfait** (pas de barème complexe au MVP).

**Dépendances :**
- Disponibilité d'un **Keycloak** opérationnel (auth/RBAC) — à confirmer en faisabilité (Phase 03).
- Serveur de tâches planifiées (cron / scheduler Spring) pour le job batch des alertes.


---

## 10. Score de maturité (/20)

| Axe | Note (0–4) |
|-----|-----------|
| Complétude | 4 |
| Qualité | 4 |
| Sécurité | 4 |
| Traçabilité | 4 |
| Automatisation | 1 |
| **Total** | **17/20** |

> Lecture : **17/20 → « Solide+ »**. Par rapport à la v1.0 (16/20), les gains portent sur la Qualité (règles métier précisées : calcul loyers, honoraires, garanties) et la Sécurité (flux d'invitation spécifié, cycle de vie révocation clarifié). L'axe Automatisation reste à 1 — normal à ce stade, la CI/CD relève des phases 06–07. L'EB est prête pour la Phase 03 — Faisabilité.

---

## 11. Décision Gate 1

- **Décision (consignée par le porteur) :** ☑ ✅ **Go** · ☐ Go sous réserve · ☐ No Go
- **Justification :** tous les critères du Gate 1 sont satisfaits — périmètre in/out défini (IRL explicitement exclus), besoins fonctionnels priorisés MoSCoW avec règles métier précisées, non fonctionnels explicites dont sécurité/RGPD/audit, KPI mesurables, parties prenantes et contraintes identifiées, score 17/20 (≥ 14).
- **Points résolus en v1.1 (ne bloquent plus le Go) :**
  1. ✅ Flux d'invitation gestionnaire spécifié (token UUID 72h, multi-bailleur par Affectation).
  2. ✅ Cycle de vie après révocation défini (soft, scoping suffit).
  3. ✅ Règles de calcul loyers précisées (mois plein, charges CC, pas de prorata).
  4. ✅ Flux garanties modélisé (3 statuts, alerte J+30).
  5. ✅ Honoraires : calcul automatique uniquement, saisie libre supprimée.
  6. ✅ Moteur d'alertes : batch quotidien, modèle de données défini, anti-doublon.
  7. ✅ Audit log : granularité définie, durée de conservation précisée.
  8. ✅ Révision IRL exclue du périmètre MVP.
- **Date & responsable :** 2026-06-04 — jptshilombo@gmail.com (décideur Gate 1).

---

## Annexe A — Modèles de données de cadrage

### A.1 Entité `Alerte`

```
Alerte {
  id               UUID
  bailleurId       UUID        -- ownership (toutes requêtes scoped)
  destinataireId   UUID        -- bailleur ou gestionnaire
  type             ENUM        -- LOYER_EN_RETARD | FIN_BAIL | PREAVIS | GARANTIE_NON_RESTITUEE
  bienId           UUID
  bailId           UUID?       -- nullable
  periode          String      -- ex : "2026-06" (année-mois, clé anti-doublon)
  message          String      -- texte généré automatiquement
  statut           ENUM        -- NON_LUE | LUE
  dateCreation     Timestamp
  dateLecture      Timestamp?  -- nullable
}
```

**Contrainte anti-doublon :** index unique sur `(type, bienId, periode)` pour les alertes au statut `NON_LUE`.

**Job batch :** planifié quotidiennement (ex. 7h00 via `@Scheduled` Spring). Évalue les conditions déclenchantes pour tous les biens actifs de tous les bailleurs.

---

### A.2 Entité `AuditLog`

```
AuditLog {
  id           UUID
  bailleurId   UUID        -- ownership + requêtes scoped
  acteurId     UUID        -- utilisateur ayant réalisé l'action
  acteurRole   ENUM        -- BAILLEUR | GESTIONNAIRE
  action       ENUM        -- CREATE_AFFECTATION | REVOKE_AFFECTATION | CREATE_BAIL
                           -- UPDATE_BAIL | POINTER_PAIEMENT | UPDATE_PAIEMENT
                           -- CREATE_GARANTIE | UPDATE_GARANTIE | RESTITUER_GARANTIE
                           -- VALIDER_HONORAIRES
  entityType   String      -- ex : "Affectation", "Bail", "Paiement"
  entityId     UUID
  timestamp    Timestamp
  details      JSONB       -- snapshot léger avant/après (champs modifiés uniquement)
}
```

**Périmètre :** écritures uniquement. Les lectures ne sont pas journalisées au MVP.
**Stockage :** table dédiée dans la base PostgreSQL principale.
**Durée de conservation :** durée du bail + 3 ans (enregistrée dans le registre RGPD).
**Accès :** bailleur uniquement, sur son propre `bailleurId`.

---

### A.3 Règles de calcul des loyers attendus (BF-33) — paiement à terme échu

| Situation | Règle MVP |
|-----------|-----------|
| Sémantique de l'échéance | Paiement **à terme échu** : une échéance porte sur un **mois consommé** (`periode`) et devient **exigible le 1er du mois suivant** (`date_exigibilite`). |
| Première période | **Mois de début** du bail (le mois d'entrée est facturé, pas perdu). Pas de prorata. |
| Dernière période | Mois civil du **terme** du bail. Pas de prorata. |
| Exigibilité | Pour une période `m`, `date_exigibilite = 1er jour de (m + 1 mois)`. |
| Montant mensuel attendu | `loyer_CC` du bail (charges comprises). Un seul montant par mois. |
| Révision de loyer | **Hors périmètre MVP** (IRL exclu). Le montant reste celui du bail jusqu'à sa clôture. |

> **Exemple :** bail démarrant le **1er mai 2026** → échéance `periode = 2026-05`, **exigible le 2026-06-01**. Au début de juin, le locataire paie l'échéance **Mai_2026**.
>
> Ces règles sont des **partis pris MVP** documentés, non des règles légales imposées. L'alerte de retard (BF-60) se calcule sur `date_exigibilite + tolérance`.

---

### A.4 Règles de calcul automatique des honoraires (BF-51)

| Type d'honoraires | Formule |
|-------------------|---------|
| `POURCENTAGE` | `honoraires_mois = montantHonoraires (%) × loyer_encaissé_du_mois` |
| `FORFAIT` | `honoraires_mois = montantHonoraires` (fixe, indépendant du paiement reçu) |

Calcul déclenché automatiquement à chaque pointage de paiement ou en fin de mois par le job batch.
Statuts de suivi : `DU → EN_ATTENTE → PAYE` (validation par le bailleur uniquement).

---

### A.5 Statuts de la garantie locative (BF-41/42)

```
DETENU ──────────────────────► RESTITUE_TOTAL
   │
   └──── (retenue partielle) ──► RESTITUE_PARTIEL ──► RESTITUE_TOTAL
```

| Statut | Description |
|--------|-------------|
| `DETENU` | Garantie en possession du bailleur. Statut par défaut à la création. |
| `RESTITUE_PARTIEL` | Restitution partielle effectuée ; retenue enregistrée avec motif texte libre. |
| `RESTITUE_TOTAL` | Garantie intégralement restituée au locataire. |

**Alerte associée :** si statut = `DETENU` et bail terminé depuis > X jours (défaut : 30), une alerte `GARANTIE_NON_RESTITUEE` est générée (cf. BF-63).

---
*Livrable CGPA v1.2 — Phase 02 (Expression du besoin). Mis à jour le 2026-06-04 : v1.1 décisions de cadrage intégrées (IRL exclu, modèles de données annexés) ; v1.2 sémantique « terme échu » précisée (BF-33 / A.3), alignée sur le DAT Phase 05. ✅ Verrou de codage levé : Gates 1→4 statués Go (Gate 4 le 2026-06-04). Prochaine phase : 06 — Planification Agile (backlog).*