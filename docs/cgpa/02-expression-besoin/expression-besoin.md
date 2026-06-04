# Expression du besoin (EB) — LoyerTracker

| Champ | Valeur |
|-------|--------|
| Projet | LoyerTracker |
| Auteur | jptshilombo@gmail.com |
| Date | 2026-06-04 |
| Phase | 02 — Expression du besoin |
| Gate visé | Gate 1 (verrou de codage) |
| Statut | En revue |
| Prérequis | Fiche idée v3 validée — Gate 0 Go (15/20) |

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
- Gestion des comptes **Bailleur** et **Gestionnaire** (invitation du gestionnaire par le bailleur).
- Gestion des **biens** (CRUD, statut : libre / loué / en travaux).
- Gestion des **baux** (1 bail actif par bien : locataire, loyer, charges, dates, dépôt de garantie).
- **Affectation** d'un gestionnaire à un bien (entité pivot), avec honoraires et statut.
- **Rotation** de gestionnaire (révocation + nouvelle affectation) avec conservation de l'historique.
- **Suivi des paiements** de loyer mois par mois (statut : reçu / partiel / en retard / impayé).
- **Suivi des garanties locatives** (dépôt : montant, type, état jusqu'à restitution).
- **Suivi des honoraires** du gestionnaire (dû / en attente / payé par période).
- **Alertes d'échéances** : loyer en retard, fin de bail, préavis, révision IRL.
- **Tableaux de bord** différenciés : bailleur (tous biens) / gestionnaire (biens affectés actifs).
- **Cloisonnement des accès** (RBAC) : un gestionnaire ne voit que ses affectations actives ; aucun accès cross-bailleur.

### 2.2 Exclus (hors MVP — roadmap)
| Élément | Décision | Justification |
|---------|----------|---------------|
| Génération de **quittance de loyer PDF** | Should (post-MVP, lot 2) | Forte valeur mais non bloquant pour le cœur « suivi + alertes ». **À confirmer porteur.** |
| **Mandat de gestion PDF** généré depuis l'Affectation | Could (post-MVP) | Confort, non critique au MVP. **À confirmer porteur.** |
| **Rapprochement bancaire** automatique (agrégation comptes) | Won't (MVP) | Complexité + coût d'intégration ; saisie manuelle au MVP. |
| **Aide à la déclaration des revenus fonciers** | Won't (MVP) | Fonctionnalité comptable avancée, hors cœur. |
| **Application mobile native** | Won't (MVP) | Web responsive suffit au MVP ; natif en roadmap. |
| **Paiement en ligne / encaissement** intégré | Won't (MVP) | Hors périmètre de suivi ; implications réglementaires (DSP2). |
| **Multi-bailleur sur un même compte / SCI multi-associés** | Won't (MVP) | 1 compte = 1 bailleur propriétaire au MVP. |

> ⚠️ **Risque de scope creep** identifié : les 3 premières lignes (quittance, mandat, rapprochement) sont les extensions les plus demandées. Le cadrage strict du MVP est une exigence du Gate 1.

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
| BF-01 | Le bailleur peut créer son compte et s'authentifier. | Must |
| BF-02 | Le bailleur peut **inviter** un gestionnaire (qui crée son compte sur invitation). | Must |
| BF-03 | Le gestionnaire peut créer son compte uniquement via une invitation. | Must |
| BF-04 | Le système attribue les rôles **Bailleur** / **Gestionnaire** et applique les permissions associées. | Must |

### 4.2 Biens & baux
| ID | Besoin | Priorité |
|----|--------|----------|
| BF-10 | Le bailleur peut créer / modifier / archiver un **bien** (caractéristiques, statut). | Must |
| BF-11 | Le bailleur (ou le gestionnaire affecté) peut enregistrer un **bail** : locataire, loyer, charges, dates début/fin, dépôt de garantie, conditions. | Must |
| BF-12 | Le système garantit **un seul bail actif par bien** à la fois. | Must |
| BF-13 | Le bailleur peut consulter l'historique des baux d'un bien. | Should |

### 4.3 Affectation & rotation
| ID | Besoin | Priorité |
|----|--------|----------|
| BF-20 | Le bailleur peut créer une **Affectation** (gestionnaire + bien + honoraires + dates). | Must |
| BF-21 | Le système garantit **un seul gestionnaire ACTIF par bien** à la fois. | Must |
| BF-22 | Le bailleur peut **révoquer** une affectation active. | Must |
| BF-23 | Le bailleur peut créer une nouvelle affectation après révocation (**rotation**) sans perte d'historique. | Must |
| BF-24 | Bailleur et gestionnaire peuvent consulter l'**historique des affectations** (selon leur périmètre). | Should |

### 4.4 Suivi des paiements
| ID | Besoin | Priorité |
|----|--------|----------|
| BF-30 | Pointer un loyer mois par mois avec un statut : **reçu / partiel / en retard / impayé**. | Must |
| BF-31 | Visualiser l'**historique des paiements** par bien et par période. | Must |
| BF-32 | Enregistrer un paiement **partiel** (montant + reste dû). | Should |
| BF-33 | Le système calcule automatiquement les **loyers attendus** par mois à partir du bail. | Must |

### 4.5 Garanties locatives
| ID | Besoin | Priorité |
|----|--------|----------|
| BF-40 | Enregistrer un **dépôt de garantie** : montant, type (caution, garant, Visale…), date de versement. | Must |
| BF-41 | Suivre l'**état** de la garantie jusqu'à sa restitution (détenu / restitué partiel / restitué). | Must |
| BF-42 | Tracer les **retenues** sur dépôt à la restitution. | Should |

### 4.6 Honoraires
| ID | Besoin | Priorité |
|----|--------|----------|
| BF-50 | Définir les honoraires d'une affectation : type (**POURCENTAGE / FORFAIT**) + montant. | Must |
| BF-51 | Le gestionnaire peut **saisir** les honoraires dus par période ; le bailleur les **valide / consulte**. | Should |
| BF-52 | Suivre le statut des honoraires : **dû / en attente / payé**. | Should |

### 4.7 Alertes & échéances
| ID | Besoin | Priorité |
|----|--------|----------|
| BF-60 | Alerter sur un **loyer en retard** (au-delà de la date d'échéance + tolérance paramétrable). | Must |
| BF-61 | Alerter sur une **fin de bail** approchante (J-X paramétrable). | Must |
| BF-62 | Alerter sur une **échéance de préavis**. | Must |
| BF-63 | Alerter sur une **révision de loyer (IRL)** annuelle. | Should |
| BF-64 | Le destinataire de l'alerte respecte le **cloisonnement** : bailleur (tous biens) / gestionnaire (biens affectés). | Must |
| BF-65 | Choisir le **canal** d'alerte (in-app au minimum ; e-mail Should). | Should |

### 4.8 Tableaux de bord & cloisonnement
| ID | Besoin | Priorité |
|----|--------|----------|
| BF-70 | **Tableau de bord Bailleur** : vue consolidée tous biens (encaissements du mois, retards, prochaines échéances). | Must |
| BF-71 | **Tableau de bord Gestionnaire** : limité aux biens affectés actifs. | Must |
| BF-72 | Le système **interdit tout accès** d'un gestionnaire à un bien non affecté ou à un autre bailleur. | Must |
| BF-73 | Le système **journalise** les actions sensibles (affectation, révocation, modification de bail/paiement). | Should |

---

## 5. Besoins non fonctionnels

| ID | Catégorie | Exigence |
|----|-----------|----------|
| BNF-01 | **Sécurité — Auth** | Authentification centralisée via **Keycloak / OIDC** ; rôles Bailleur/Gestionnaire en RBAC. |
| BNF-02 | **Sécurité — Cloisonnement** | Toute requête de données est **scoped par `bailleurId`** et, pour un gestionnaire, restreinte à ses affectations **actives**. Aucun accès cross-bailleur possible (vérifié côté serveur, jamais seulement côté UI). |
| BNF-03 | **Sécurité — Moindre privilège** | Permissions minimales par rôle (cf. matrice fiche idée §6) ; secrets gérés hors dépôt. |
| BNF-04 | **RGPD** | Données locataires/bailleurs minimisées ; base légale documentée ; droits d'accès/rectification/effacement ; durée de conservation définie ; registre de traitement. |
| BNF-05 | **Traçabilité / Audit** | Journalisation horodatée des actions sensibles (création/révocation d'affectation, modif. bail/paiement/garantie) avec auteur. |
| BNF-06 | **Performance** | Affichage d'un tableau de bord < 2 s pour un portefeuille jusqu'à ~50 biens. |
| BNF-07 | **Disponibilité** | Cible MVP : best-effort (self-hosted) ; sauvegardes régulières de la base ; pas d'engagement SLA au MVP. |
| BNF-08 | **Fiabilité des alertes** | Une alerte due est émise au plus tard à J+1 de la condition déclenchante ; pas de doublon non maîtrisé. |
| BNF-09 | **Portabilité** | Déploiement conteneurisé (**Docker**), self-hosting possible ; configuration externalisée. |
| BNF-10 | **Maintenabilité / Qualité** | Tests unitaires + intégration ; Définition de « Terminé » (tests + doc + sécurité) respectée. |
| BNF-11 | **Compatibilité / Accès** | Web **responsive** (consultation mobile des alertes et du tableau de bord). |
| BNF-12 | **Internationalisation / Devise** | Langue **FR**, devise **EUR**, fuseau Europe/Paris au MVP. |
| BNF-13 | **Sauvegarde / Réversibilité** | Export des données du bailleur (portabilité RGPD) ; rollback de déploiement prévu (phases ultérieures). |

---

## 6. Critères de succès (KPI)

| KPI | Cible MVP |
|-----|-----------|
| **Couverture du suivi** : part des loyers attendus ayant un statut renseigné dans le mois | ≥ 95 % |
| **Sécurité du cloisonnement** : accès non autorisés cross-bailleur / cross-affectation | **0** (contrôle bloquant) |
| **Fiabilité des alertes** : échéances critiques (retard, fin de bail, préavis) signalées à temps | ≥ 99 % |
| **Intégrité de la rotation** : rotations de gestionnaire sans perte d'historique | 100 % |
| **Délai de saisie** d'un pointage de paiement | < 30 s |
| **Adoption** : le porteur cesse d'utiliser son tableur pour le suivi | Oui/Non (binaire, à 1 mois) |

---

## 7. Contraintes

- **Stack de référence :** Spring Boot · Angular · Keycloak (OIDC/RBAC) · PostgreSQL · Docker · CI/CD. *(Confirmation de faisabilité en Phase 03.)*
- **Ressources :** développeur solo, projet personnel → MVP à périmètre maîtrisé.
- **Conformité :** RGPD obligatoire dès la conception (Shift-Left).
- **Budget :** minimal (auto-hébergement, coûts d'infra réduits).
- **Délais :** non contraints par une date externe ; cadence Agile par sprints.

---

## 8. Hypothèses & dépendances

**Hypothèses (à valider) :**
- [ ] Le bailleur cible engage réellement **plusieurs gestionnaires** sur des biens différents.
- [ ] La **saisie manuelle** des paiements est acceptable au MVP (pas de rapprochement bancaire).
- [ ] La **quittance PDF** peut rester hors MVP (lot 2) sans bloquer l'adoption. *(décision porteur attendue)*
- [ ] Le **mandat de gestion PDF** est un confort post-MVP. *(décision porteur attendue)*
- [ ] La structure d'honoraires se limite à **% ou forfait** (pas de barème complexe au MVP).

**Dépendances :**
- Disponibilité d'un **Keycloak** opérationnel (auth/RBAC) — à confirmer en faisabilité.
- Canal d'envoi d'**e-mail** si BF-65 (alertes e-mail) est retenu au MVP.

---

## 9. Score de maturité (/20)

| Axe | Note (0–4) |
|-----|-----------|
| Complétude | 4 |
| Qualité | 3 |
| Sécurité | 4 |
| Traçabilité | 4 |
| Automatisation | 1 |
| **Total** | **16/20** |

> Lecture : **16/20 → « Solide »**. Périmètre in/out cadré, besoins fonctionnels priorisés MoSCoW, non fonctionnels explicites (sécurité/cloisonnement/RGPD au premier plan), KPI mesurables. L'axe *Automatisation* reste bas (normal à ce stade : la CI/CD relève des Phases 06–07). Réserves mineures : 2 décisions de périmètre (quittance / mandat PDF) à confirmer par le porteur.

---

## 10. Décision Gate 1

- **Décision recommandée :** ☑ ✅ **Go** · ☐ Go sous réserve · ☐ No Go
- **Justification :** tous les critères du Gate 1 sont satisfaits — périmètre in/out défini, besoins fonctionnels priorisés (MoSCoW), non fonctionnels explicites dont sécurité/RGPD, KPI mesurables, parties prenantes et contraintes identifiées, score 16/20 (≥ 14).
- **Points à trancher avant / en début de Phase 03 (n'empêchent pas le Go) :**
  1. **Quittance de loyer PDF** : confirmer MVP (Should) ou inclusion immédiate.
  2. **Mandat de gestion PDF** : confirmer post-MVP (Could).
  3. **Canal d'alerte e-mail** (BF-65) : in-MVP ou in-app seulement au départ ?
- **Date & responsable :** 2026-06-04 — jptshilombo@gmail.com (décideur Gate 1).

---
*Livrable CGPA v1.0 — Phase 02 (Expression du besoin). ⛔ Verrou de codage maintenu : Gates 1→4 requis en Go avant tout développement applicatif. Prochaine phase : 03 — Faisabilité (Gate 2).*
