# Addendum Backlog — Epic EP-09 Patrimoine & affectation enrichie

| Champ | Valeur |
|-------|--------|
| Document de référence | `product-backlog.md` (✅ Validé — Gate 5 Go, 2026-06-04) — **non modifié** |
| Statut | **Accepté — Plan d'Exécution Patrimoine approuvé (GO) par le PO le 2026-06-21** ; développement autorisé à démarrer (Sprint 1, `plan-execution-patrimoine.md`) |
| Date | 2026-06-21 |
| Décision liée | D-PAT-001 / ADR-11, addendum CDC (EF-90→96, RM-90→99) |

> ⚠️ **Réserve de renumérotation.** La décision métier source nommait ces stories **US-70 à US-75**. Ces identifiants sont **déjà occupés** par le backlog validé Gate 5 (`product-backlog.md` EP-08 : US-70 RGPD export/effacement, US-71 suite de tests d'autorisation, US-72 SAST/secrets/Nginx). Pour préserver la traçabilité existante (interdiction CGPA de modifier un backlog déjà validé), les stories sont renumérotées **US-80 → US-85** sous un nouvel epic **EP-09**.

## EP-09 — Patrimoine & affectation enrichie

| ID | Epic | Jalons | Priorité |
|----|------|--------|----------|
| EP-09 | **Patrimoine & affectation enrichie** — regroupement de biens, typologie administrable, affectation à deux granularités | Post-MVP (lot post-go-live, sous Phase 7) | Must (cœur du modèle) / Should (raffinements) |

---

### US-80 — Gestion des patrimoines *(ex US-70 source)*

**En tant que** bailleur, **je veux** créer, renommer et archiver des patrimoines **afin de** regrouper logiquement mes biens.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un bailleur authentifié **W** il crée un patrimoine (nom) **T** le patrimoine est persisté, scopé à son `bailleurId`, visible uniquement par lui. **G** un patrimoine existant **W** il le renomme ou l'archive **T** changement persisté ; un patrimoine archivé n'apparaît plus dans les listes de rattachement de nouveaux biens (EF-92). |
| Dépendances | Aucune (peut démarrer dès l'approbation du Plan d'Exécution) ; bloque US-82 |
| Priorité | Must |
| Points | 3 |
| Risques | Migration des biens existants vers un patrimoine par défaut (cf. addendum CDC §4.5) — à traiter dans cette story, pas dans une story séparée ; l'endpoint d'archivage devra être étendu en Sprint 2 (US-84) avec la garde RS-06 (rejet 400 si affectation patrimoine `ACTIVE`), une fois `Affectation.patrimoineId` disponible |
| Source | EF-90, RM-90/91 |

---

### US-81 — Typologie des biens *(ex US-71 source)*

**En tant que** bailleur, **je veux** choisir le type d'un bien dans une liste administrable **afin de** classer mon patrimoine de façon homogène.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un bailleur créant/modifiant un bien **W** il choisit un type **T** seules les valeurs `Appartement`/`Boutique`/`Bureau`/`Villa`/`Terrain`/`Entrepôt`/`Autre` (ou toute valeur active du référentiel) sont acceptées ; une valeur hors liste est rejetée (400). **G** les biens existants **W** la migration s'exécute **T** chaque `type` libre existant est mappé vers un code du référentiel, avec une liste des valeurs non mappables automatiquement remontée pour arbitrage manuel. |
| Dépendances | Aucune dépendance bloquante ; recommandé en parallèle de US-80 |
| Priorité | Must |
| Points | 3 |
| Risques | Perte d'information si des valeurs de `Bien.type` existantes (saisie libre actuelle) ne correspondent à aucun code — nécessite un arbitrage PO explicite avant migration (liste des valeurs distinctes actuellement en base à produire en Sprint 1) |
| Source | EF-91, RM-93 |

---

### US-82 — Rattachement Bien → Patrimoine *(ex US-72 source)*

**En tant que** bailleur, **je veux** que chaque bien soit obligatoirement rattaché à un patrimoine **afin de** garantir la cohérence du regroupement.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un bailleur créant un bien **W** il ne renseigne pas de `patrimoineId` **T** la création est rejetée (400). **G** un `patrimoineId` d'un autre bailleur **W** création **T** rejet 403/404 (cohérence ADR-01). **G** les biens existants **W** la migration s'exécute **T** 100 % des biens existants sont rattachés à un patrimoine (par défaut si nécessaire), 0 bien orphelin après migration. |
| Dépendances | Bloquée par US-80 (le patrimoine doit exister avant le rattachement) |
| Priorité | Must |
| Points | 5 |
| Risques | Risque de régression sur les endpoints `biens` existants (CRUD déjà en production) — nécessite une suite de non-régression complète avant fusion |
| Source | EF-92, RM-92 |

---

### US-83 — Unicité du contrat actif *(ex US-73 source — déjà satisfait, story de clarification)*

**En tant que** bailleur, **je veux** être assuré qu'un seul contrat est actif par bien à la fois **afin d'**éviter toute incohérence locative.

| Champ | Détail |
|-------|--------|
| Constat | Cette règle est **déjà implémentée** : `Bail.statut` (`ACTIF`/`CLOS`) + index `uq_bail_actif` (migration V1, EF-12/US-21). « Contrat » dans la décision métier désigne l'entité technique `Bail` (cf. ADR-11). |
| Critères d'acceptation (GWT) | **G** la suite de tests existante (`SchemaMigrationTest`, tests US-21) **W** elle s'exécute après l'introduction du Patrimoine **T** aucune régression : un second bail actif sur un bien reste rejeté (409), quel que soit le patrimoine de rattachement du bien. |
| Dépendances | Aucune — story de **non-régression**, pas de développement nouveau |
| Priorité | Should *(recommandation : reclasser en simple critère de non-régression du Sprint 3, pas en story autonome — cf. réserve ci-dessous)* |
| Points | 1 (vérification uniquement) |
| Risques | Aucun risque de développement ; risque documentaire si elle est traitée comme un développement neuf alors qu'elle n'en nécessite pas |
| Source | EF-96 (= EF-12 existant), RM-94 |

> **Réserve :** il est recommandé de **fermer US-83 sans développement** et de la convertir en critère GO du Sprint 3 (« non-régression EF-12 confirmée ») plutôt que de la maintenir comme story à part entière, pour éviter de surestimer la charge du lot.

---

### US-84 — Affectation gestionnaire par patrimoine *(ex US-74 source)*

**En tant que** bailleur, **je veux** affecter un gestionnaire à un patrimoine entier **afin de** déléguer la gestion d'un groupe de biens en une seule opération.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un bailleur et un gestionnaire invité **W** il crée une affectation portant un `patrimoineId` (pas de `bienId`) **T** affectation `ACTIVE` persistée ; le gestionnaire accède en lecture/écriture (selon matrice de rôles) à tous les biens actuels du patrimoine. **G** un nouveau bien ajouté au patrimoine après l'affectation **W** le gestionnaire consulte son périmètre **T** le nouveau bien est accessible sans nouvelle affectation (héritage dynamique, pas de duplication). **G** un patrimoine avec au moins une affectation patrimoine `ACTIVE` **W** le bailleur tente d'archiver ce patrimoine (endpoint US-80) **T** la requête est rejetée (400, **RS-06**) ; le bailleur doit d'abord révoquer explicitement ces affectations (cohérent EF-22). |
| Dépendances | Bloquée par US-80 et US-82 ; bloque US-85 (la priorité bien/patrimoine ne peut être testée sans affectation patrimoine fonctionnelle) |
| Priorité | Must |
| Points | 5 |
| Risques | Extension d'`AuthorizationService` (ADR-02) — zone de risque sécurité prioritaire ; **dépend de la validation PO de l'algorithme RM-98** avant codage *(acquis le 2026-06-21)* ; nécessite d'étendre l'endpoint d'archivage déjà livré en Sprint 1 (US-80) avec la garde RS-06 — risque d'oubli si traité comme un simple ajout de modèle |
| Source | EF-93, RM-95 |

---

### US-85 — Affectation fine par bien (priorité & exceptions) *(ex US-75 source)*

**En tant que** bailleur, **je veux** affecter ou restreindre un gestionnaire sur un bien précis, même au sein d'un patrimoine déjà affecté **afin d'**ajuster finement la délégation sans tout réaffecter.

| Champ | Détail |
|-------|--------|
| Critères d'acceptation (GWT) | **G** un gestionnaire avec affectation patrimoine `ACTIVE` **W** le bailleur crée une affectation bien `EXCLUSION` sur un bien de ce patrimoine, pour ce gestionnaire **T** le gestionnaire perd l'accès à ce bien précis, conserve l'accès aux autres biens du patrimoine. **G** un gestionnaire sans affectation patrimoine **W** le bailleur crée une affectation bien `INCLUSION` **T** le gestionnaire accède à ce seul bien, comme aujourd'hui (US-23, comportement inchangé). **G** une affectation bien et une affectation patrimoine simultanées pour le même gestionnaire/bien **W** résolution du périmètre **T** l'affectation bien prévaut (EF-95). |
| Dépendances | Bloquée par US-84 (la priorité ne se teste qu'avec une affectation patrimoine existante) |
| Priorité | Must |
| Points | 8 *(la plus complexe : couvre les 4 combinaisons de résolution RM-98)* |
| Risques | **Risque majeur** : algorithme de résolution non explicitement validé par le PO (RM-98, cf. ADR-11) — critère GO dédié exigé avant codage ; risque de fuite cross-bien si la résolution n'est pas centralisée dans `AuthorizationService` |
| Source | EF-94/95, RM-96/97/98 |

---

## Récapitulatif & priorisation

| Epic | Stories | Total pts | Must | Should |
|------|---------|-----------|------|--------|
| EP-09 Patrimoine & affectation enrichie | US-80→85 | 25 | 24 | 1 |

> Charge indicative : 25 pts ÷ ~20 pts/sprint (vélocité de référence du projet) ≈ **1,5 sprint** de développement pur, mais découpée en **3 sprints gouvernés** dans le Plan d'Exécution (`plan-execution-patrimoine.md`) pour isoler la zone de risque sécurité (US-84/85) et permettre une validation PO intermédiaire de RM-98.

## Dépendances & risques (synthèse)

| Élément | Type | Impact |
|---------|------|--------|
| US-80 doit précéder US-82 et US-84 | Dépendance technique | Bloquant Sprint 1 |
| US-82 doit précéder US-84 | Dépendance technique | Bloquant Sprint 1→2 |
| US-84 doit précéder US-85 | Dépendance métier | Bloquant Sprint 2→3 |
| Validation PO de l'algorithme RM-98 | Risque gouvernance | Bloquant avant tout codage de US-84/85 |
| Migration des `Bien.type` libres existants | Risque donnée | À cadrer en Sprint 1 (US-81) avant toute migration |
| US-83 ne nécessite aucun développement | Risque de surestimation | Reclasser en critère GO de non-régression (Sprint 3) |
