# Addendum transverse — EP-15 « Gestion des personnes »

| Champ | Valeur |
|---|---|
| Date | 2026-07-08 |
| Statut | **Cadrage documentaire proposé** — aucun code, aucune migration exécutée. Point de kickoff **K1** à trancher par le PO avant tout Sprint |
| Epic | **EP-15** (renuméroté depuis la demande initiale « EP-14 », déjà pris par les Quittances certifiées, release `1.9.0` ; EP-13 réservé « Fin de bail ») |
| Décision d'architecture | `docs/cgpa/05-architecture-conception/adr/ADR-16-gestion-personnes.md` (D-PERS-001) |
| Portée | Rendre les **Gestionnaires** et les **Locataires** des entités métier durables (statuts, historique, audit, anti-doublons), indépendantes des `Bail`/`Affectation` |

Ce document est un **résumé exécutif transverse** reliant les livrables produits pour ce
cadrage. Il ne remplace aucun d'entre eux et ne duplique pas leur contenu détaillé.

## 1. Documents produits pour ce cadrage

| Document | Rôle |
|---|---|
| `docs/cgpa/02-expression-besoin/addendum-personnes.md` | Extension du besoin (BF-97→101), précision — non contradiction — de la classification « Locataire = sujet de données non-utilisateur » (Gate-1) |
| `docs/cgpa/04-cahier-des-charges/addendum-personnes.md` | Exigences détaillées (EF-97→107), registre de règles métier (RM-100→107), modèle de données proposé, migrations, endpoints REST, matrice de traçabilité |
| `docs/cgpa/05-architecture-conception/adr/ADR-16-gestion-personnes.md` | Décision d'architecture : statut global Gestionnaire, RLS Locataire, fonction `SECURITY DEFINER` cross-tenant, `bytea` pour la photo, registre de risques RSV-EP15 |
| `docs/cgpa/05-architecture-conception/dossier-architecture.md` §3.5 | Extension additive du modèle de données déjà validé (Gate 4), sans réécriture des sections existantes |
| `docs/cgpa/06-planification-agile/addendum-backlog-ep15-personnes.md` | Epic EP-15, User Stories US-105→114, réparties en 3 sprints |
| `docs/cgpa/06-planification-agile/plan-execution-ep15-personnes.md` | Kickoff, séquencement des sprints, critères GO, checklist CGPA |

## 2. Pourquoi un ADR était nécessaire

Ce cadrage introduit **quatre décisions d'architecture réelles**, pas de simples ajouts de
colonnes :

1. Un statut de compte **global** sur une entité jusqu'ici sans statut ni RLS (`Gestionnaire`),
   avec une conséquence cross-bailleur assumée (RSV-EP15-01).
2. Une nouvelle table sous RLS (`Locataire`), premier cas d'extraction d'un champ texte libre
   vers une entité de domaine complète avec cycle de vie sur ce projet.
3. Un nouveau patron technique : une fonction `SECURITY DEFINER` **cross-tenant** (traversant
   la RLS d'`affectation` pour un pré-check global), distincte des fonctions publiques
   existantes (V22) qui, elles, servaient une vérification par capability sans contexte
   tenant.
4. Une convention de stockage binaire (`photo`) formalisée pour des entités « personne »,
   réutilisant mais généralisant le précédent `quittance.pdf`.

Sans ADR-16, ces quatre décisions auraient été prises implicitement au fil du code — contraire
à la discipline CGPA (traçabilité, aucune décision d'architecture implicite).

## 3. Analyse d'impact — synthèse

| Domaine | Impact | Régression ? |
|---|---|---|
| Patrimoine, Bien, Affectation, Garantie, Paiements, Honoraires, Alertes, Dashboard, Ledger, Money, Documents PDF, QR Code | **Aucun changement de code envisagé** dans ces modules | Non — aucun endpoint ni migration de ces modules n'est touché |
| Bail | `locataireNom`/`locataireEmail` (texte) remplacés par `locataireId` (FK) — **rupture de contrat HTTP** à la bascule (Sprint C, V24) | Changement assumé et documenté (ADR-16), à communiquer explicitement dans les release notes du sprint concerné |
| RGPD (ADR-03) | Cible de l'anonymisation déplacée de `Bail` vers `Locataire` — amélioration (couvre tout l'historique d'une personne en une opération) | Non-régression exigée sur les tests RGPD existants (US-114) |
| RBAC (ADR-02) | Aucun nouveau rôle ; toutes les actions restent réservées à `BAILLEUR` ; un Gestionnaire n'administre jamais un autre Gestionnaire (RM-107, inchangé) | Non |
| Sécurité multi-tenant (ADR-01) | Le Gestionnaire reste hors RLS (inchangé) ; le Locataire rejoint le pattern RLS standard ; nouvelle fonction `SECURITY DEFINER` à surface minimale (booléen) | Revue sécurité dédiée requise avant Gate Staging du Sprint A (nouveau patron cross-tenant) |
| Audit | Nouveaux points d'audit uniquement ; aucun point existant modifié | Non |
| Infrastructure | Aucune nouvelle brique (pas de stockage objet/fichier — réutilisation `bytea`/`pg_dump`) | Non |
| `docs/prod-state.md` | **Vérifié — aucune modification requise** à ce stade (aucun déploiement, phase 100 % documentaire) | — |

## 4. Ce qui reste ouvert avant tout Sprint

- **K1** (ADR-16, `plan-execution-ep15-personnes.md`) : sémantique de « créer » un Gestionnaire
  — proposition par défaut documentée, décision PO explicite requise.
- **Approbation du Plan d'Exécution** par le PO (GO explicite), conformément à CLAUDE.md — condition
  bloquante avant tout codage.

## 5. Checklist de validation CGPA globale

- [x] `docs/project-state.md` consulté avant toute rédaction (phase CGPA v5.4.1, post-clôture `1.9.0`)
- [x] Aucune décision, Gate, ADR ou risque historique supprimé, réécrit ou rejoué
- [x] Numérotation vérifiée sans collision : Epic **EP-15** (EP-13/14 exclus), US **105→114**,
      ADR **16**, EF **97→107**, RM **100→107**, ENF **91/92**, Flyway **V23/V24**
- [x] Seuls des addenda ont été produits ; `fiche-idee.md`, `expression-besoin.md`,
      `cahier-des-charges.md`, `product-backlog.md` **non modifiés en place**
- [x] `dossier-architecture.md` étendu de façon strictement additive (§3.5 nouveau, aucune
      section existante altérée, tableau ADR §7 non touché — cohérent avec le traitement déjà
      réservé aux ADR-09→15)
- [x] Impact Production vérifié : aucun (documentaire uniquement)
- [ ] Kickoff K1 tranché par le PO
- [ ] GO explicite du PO sur le Plan d'Exécution avant tout codage

## 6. Prochaine étape

Soumettre ce cadrage (ADR-16, addenda EB/CDC/Backlog, Plan d'Exécution) à validation du PO :
trancher K1, puis GO explicite sur le Plan d'Exécution avant le démarrage du Sprint A.
