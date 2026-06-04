# Fiche idée — LoyerTracker

| Champ | Valeur |
|-------|--------|
| Projet | LoyerTracker |
| Auteur | jptshilombo@gmail.com |
| Date | 2026-06-03 |
| Phase | 01 — Idée & opportunité |
| Gate visé | Gate 0 |
| Statut | ✅ Validé — Gate 0 Go (v3, modèle Bailleur/Gestionnaire/Affectation) |

---

## 1. Idée en une phrase

> **LoyerTracker** est une application de gestion locative dans laquelle le **bailleur propriétaire** centralise ses biens, ses baux et ses paiements, et peut déléguer la gestion opérationnelle de chaque bien à un ou plusieurs **gestionnaires mandataires** — avec cloisonnement strict des accès, rotation possible des gestionnaires, historique conservé, alertes automatiques sur les échéances critiques et traçabilité des honoraires — afin que le bailleur reste maître de ses données tout en s'appuyant sur des tiers de confiance.

---

## 2. Problème résolu

Lorsqu'un propriétaire délègue la gestion de certains biens à des tiers (membre de la famille, cabinet léger, gestionnaire de confiance), la collaboration repose aujourd'hui sur des outils non faits pour ça : tableur partagé, échanges WhatsApp, dossiers papier transmis à la main.

**Douleurs actuelles :**
- **Aucune séparation des rôles** : bailleur et gestionnaire travaillent sur les mêmes fichiers sans traçabilité des actions de chacun.
- **Pas de vision consolidée pour le bailleur** : il dépend des rapports du gestionnaire pour connaître l'état réel de ses encaissements.
- **Rotation des gestionnaires non gérée** : changer de gestionnaire implique de transférer manuellement tout l'historique — avec risque de perte.
- **Honoraires opaques** : la rémunération du gestionnaire n'est pas tracée, source d'incompréhensions récurrentes.
- **Alertes inexistantes** : loyers en retard, fins de bail, préavis, révisions IRL — tout repose sur la mémoire humaine.
- **Dépôts de garantie mal tracés** : montant, conditions et échéance de restitution dispersés, source fréquente de litige.

---

## 3. Valeur apportée

- **Pour le Bailleur :** visibilité complète et temps réel sur tous ses biens, avec délégation maîtrisée — il reste propriétaire de ses données même en cas de changement de gestionnaire.
- **Pour le Gestionnaire :** un espace de travail clair, limité aux biens qui lui sont affectés, avec les outils nécessaires pour opérer efficacement.
- **Différenciation :** modèle **bailleur-centré avec délégation fine par bien**, rotation des gestionnaires sans perte d'historique, cloisonnement strict des accès — introuvable dans un tableur, rarement adressé par les SaaS grand public.

---

## 4. Modèle relationnel

```
Bailleur (compte principal — propriétaire des données)
  ├── crée et possède N Biens
  ├── enregistre N Baux (1 bail par bien actif)
  ├── engage N Gestionnaires via une Affectation
  │     └── 1 Affectation = 1 Gestionnaire + 1 Bien + statut + honoraires
  │           ├── 1 seul gestionnaire ACTIF par bien à la fois
  │           ├── rotation possible (révocation + nouvelle affectation)
  │           └── historique des affectations conservé
  └── supervise tout (accès complet à tous ses biens)

Gestionnaire (rôle délégué — invité par le Bailleur)
  └── voit et opère uniquement sur les biens affectés ACTIFS
        ├── pointe les paiements
        ├── suit les garanties
        └── est rémunéré via les honoraires de l'Affectation
```

---

## 5. Entité pivot : Affectation

L'`Affectation` est le cœur du modèle — elle porte la relation entre un Bailleur, un Bien et un Gestionnaire.

```
Affectation {
  id
  bailleurId        → propriétaire du bien
  bienId            → bien concerné
  gestionnaireId    → gestionnaire désigné
  statut            : ACTIVE | REVOQUEE | EXPIREE
  dateDebut
  dateFin           : nullable (mandat à durée indéterminée possible)
  typeHonoraires    : POURCENTAGE | FORFAIT
  montantHonoraires : valeur numérique
  dateCreation
  dateRevocation    : nullable
}
```

**Contrainte d'unicité :** un bien ne peut avoir qu'une seule `Affectation` au statut `ACTIVE` à la fois.

---

## 6. Rôles & permissions

| Action | Bailleur | Gestionnaire |
|--------|----------|-------------|
| Créer son compte | ✅ | ✅ (sur invitation) |
| Enregistrer / modifier un bien | ✅ | ❌ |
| Enregistrer / modifier un bail | ✅ | ✅ (biens affectés uniquement) |
| Pointer un paiement | ✅ | ✅ (biens affectés uniquement) |
| Suivre une garantie locative | ✅ | ✅ (biens affectés uniquement) |
| Inviter un gestionnaire | ✅ | ❌ |
| Affecter un gestionnaire à un bien | ✅ | ❌ |
| Révoquer un gestionnaire | ✅ | ❌ |
| Voir les biens non affectés | ✅ | ❌ |
| Voir les biens d'un autre bailleur | ❌ | ❌ |
| Saisir / valider les honoraires | ✅ | ✅ (saisie) / ✅ (lecture) |
| Tableau de bord complet (tous biens) | ✅ | ❌ (limité aux biens affectés) |
| Recevoir les alertes d'échéances | ✅ | ✅ (biens affectés uniquement) |
| Consulter l'historique des affectations | ✅ | ✅ (ses propres affectations) |

---

## 7. Cas d'usage principaux

1. **Gérer ses biens** : le bailleur enregistre ses biens, leurs caractéristiques et leur statut (libre, loué, en travaux).
2. **Enregistrer un bail** : locataire, bien, loyer + charges, dates de début/fin, dépôt de garantie, conditions.
3. **Déléguer un bien** : le bailleur invite un gestionnaire, crée une affectation sur un bien donné, définit les honoraires.
4. **Pointer les loyers mois par mois** : marquer un paiement reçu / partiel / en retard, visualiser l'historique par bien.
5. **Suivre les garanties locatives** : montant, type (caution, garant, Visale…), suivi jusqu'à restitution.
6. **Tracer les honoraires** : montant dû au gestionnaire par période, statut payé / en attente.
7. **Rotation de gestionnaire** : révoquer l'affectation active, créer une nouvelle — l'historique reste intact côté bailleur.
8. **Recevoir des alertes d'échéance** : loyer en retard, fin de bail, préavis, révision IRL.
9. **Tableau de bord** :
   - Bailleur : vue consolidée de tous ses biens, encaissements du mois, retards, prochaines échéances.
   - Gestionnaire : vue de ses biens affectés actifs uniquement.

---

## 8. Marché / alternatives

- **Tableur (Excel/Sheets)** : dominant, gratuit — mais sans rôles, sans alertes, sans gestion de rotation. *Vrai concurrent à battre.*
- **SaaS grand public** : Rentila, BailFacile, Gererseul, Smartloc, Ublo — riches mais payants, rarement pensés pour le modèle bailleur multi-gestionnaires avec cloisonnement fin.
- **Logiciels d'agence** : Apimo, Perizia, Immosoft — trop lourds et coûteux pour un usage personnel ou un cabinet léger.
- **Facteur différenciant :** modèle **bailleur-centré**, délégation fine par bien, rotation sans perte d'historique, self-hosting possible, données sous contrôle du bailleur.

---

## 9. Hypothèses à valider

- [ ] Le bailleur cible engage **réellement plusieurs gestionnaires** sur des biens différents (valider le cas d'usage).
- [ ] La **rotation des gestionnaires** est un besoin fréquent ou rare (impact sur la priorité de la feature).
- [ ] Les **honoraires** ont une structure homogène (% fixe vs forfait vs mixte) — à modéliser simplement.
- [ ] L'utilisateur accepte une **saisie manuelle** des paiements au MVP (pas de rapprochement bancaire automatique).
- [ ] Le besoin de **quittance de loyer PDF** fait-il partie du périmètre MVP ?
- [ ] Faut-il un **mandat de gestion PDF** généré par l'appli à partir de l'Affectation ?

---

## 10. Risques & opportunités

| Type | Élément | Impact |
|------|---------|--------|
| Risque | **Complexité RBAC** : permissions croisées Bailleur/Gestionnaire/Bien → surface d'erreur élevée | Modèle de sécurité à poser dès la phase architecture |
| Risque | **Données personnelles sensibles** (locataires, bailleurs) → conformité **RGPD** | Sécurité by design obligatoire dès l'EB |
| Risque | **Périmètre qui dérive** : quittances, revenus fonciers, rapprochement bancaire | Cadrage strict du MVP nécessaire |
| Risque | **Faible différenciation** si usage limité à 1 bailleur / 1 gestionnaire / 1 bien | Risque d'abandon / retour au tableur |
| Opportunité | **Modèle bailleur-centré multi-gestionnaires** rarement adressé par les outils simples | Niche claire, peu concurrencée |
| Opportunité | **Rotation sans perte d'historique** = argument fort vs tableur | Valeur démontrable dès le MVP |
| Opportunité | **Self-hosting** comme parti pris assumé | Différenciation crédible vs SaaS tiers |
| Opportunité | **Extensions futures** : quittances PDF, mandat de gestion, déclaration revenus fonciers, rapprochement bancaire | Roadmap de valeur claire |

---

## 11. Contraintes connues

- **Temps :** projet personnel, développeur solo → MVP à périmètre maîtrisé.
- **Stack de référence :** Spring Boot · Angular · Keycloak (OIDC/RBAC) · PostgreSQL · Docker · CI/CD.
- **Isolation des données :** toutes les requêtes sont scoped par `bailleurId` — le gestionnaire ne peut jamais accéder à des données hors de ses affectations actives.
- **Conformité :** RGPD (données locataires et bailleurs) → Shift-Left dès l'architecture.
- **Budget :** minimal (auto-hébergement / coûts d'infra réduits).

---

## 12. Score d'opportunité (/20)

| Axe | Note (0–4) |
|-----|-----------|
| Complétude | 4 |
| Qualité/robustesse | 3 |
| Sécurité | 3 |
| Traçabilité/doc | 4 |
| Automatisation | 1 |
| **Total** | **15/20** |

> Lecture : **15/20 → maturité « Solide »**. Le modèle bailleur-centré avec délégation par affectation est cohérent, différenciant et techniquement faisable. L'entité pivot `Affectation` résout proprement la rotation des gestionnaires sans perte d'historique. Les hypothèses d'usage restent à valider, et le périmètre MVP doit être cadré avant la Phase 02.

---

## 13. Décision Gate 0

- **Décision (consignée par le porteur) :** ☑ ✅ **Go** · ☐ Go sous réserve · ☐ No Go
- **Justification :** Score 15/20 (« Solide », ≥ 14), problème/valeur explicites, modèle relationnel stabilisé, entité pivot `Affectation` posée, risques identifiés, hypothèses testables. Le modèle bailleur-centré multi-gestionnaires est la différenciation principale vs tableur. Tous les critères du Gate 0 sont satisfaits.
- **Actions à porter en Phase 02 — Expression du besoin :**
  1. **Cadrer le périmètre MVP** : quittances PDF et mandat de gestion — in ou out ?
  2. **Modéliser les honoraires** : % du loyer, forfait fixe, ou les deux ?
  3. **Poser le modèle RBAC complet** : matrice Bailleur / Gestionnaire / Bien / Affectation.
  4. **Intégrer RGPD** comme exigence non fonctionnelle dès l'EB.
- **Date & responsable :** 2026-06-03 — jptshilombo@gmail.com (porteur / décideur Gate 0).

---
*Livrable CGPA v3.0 — Phase 01. Mis à jour le 2026-06-03 : modèle bailleur-centré, N gestionnaires par bailleur, affectation bien par bien, 1 gestionnaire actif par bien, entité pivot Affectation, rotation sans perte d'historique. ⛔ Verrou de codage actif jusqu'aux Gates 1→4.*
