# Plan d'Exécution — Montée de version Angular 20 → 22 (frontend)

| Champ | Valeur |
|-------|--------|
| Statut | **Proposé — cadrage uniquement, en attente de GO PO avant tout codage** |
| Date | 2026-07-06 |
| Type | Maintenance technique / dette DevSecOps (dépendances frontend) |
| Déclencheur | 4 PR dependabot bloquées en échec CI (#186, #188, #189, #190) |
| Niveau | Niveau 2 — changement de version majeure du framework frontend, aucun changement de contrat API ni de modèle de données |
| Environnement impacté | Frontend uniquement ; aucun impact backend, migration SQL, Staging ou Production tant que le lot n'est pas déployé |

## 1. Constat déclencheur

Le 2026-07-06, vérification de l'ensemble des PR dependabot ouvertes : 4 des 7 échouent en CI avec des conflits `ERESOLVE` npm, tous imputables à la même cause racine — le projet est figé sur Angular **20.3.25**, et ces 4 PR proposent chacune un sous-ensemble incompatible d'une même montée de version majeure.

| PR | Contenu proposé | Erreur constatée |
|----|------------------|-------------------|
| #186 | Groupe Angular (8 paquets `@angular/*` + CLI/build-angular/compiler-cli) → 22.0.5 | `typescript@5.8.3` ne satisfait pas `@angular-devkit/build-angular@22.0.5` (exige `>=6.0 <6.1`) |
| #188 | `typescript` 5.8.3 → 6.0.3 | `@angular-devkit/build-angular@20.3.30` (version actuelle) exige `typescript >=5.8 <6.0` — conflit inverse |
| #189 | `keycloak-angular` 20.1.0 → 22.0.0 | exige `@angular/common@^22`, encore en `20.3.25` |
| #190 | `eslint` 9.39.4 → 10.6.0 | conflit avec `angular-eslint@20.7.0` (actuel), qui exige `eslint ^8.57.0 || ^9.0.0` |

Aucune de ces 4 PR n'est mergeable isolément. Elles ne doivent **pas** être fermées manuellement sans remplacement : elles seront rendues obsolètes automatiquement par dependabot une fois `main` aligné sur le lot ci-dessous — même mécanisme observé le 2026-07-06 avec la PR #193 (bump partiel `codeql-action/analyze`), superseded par la PR #194 (correctif coordonné).

## 2. Analyse d'impact (vérification statique uniquement, aucun code produit)

### 2.1 Compatibilité runtime déjà vérifiée

`@angular/cli@22.0.5` exige Node `^22.22.3 || ^24.15.0 || >=26.0.0`. La CI (`node-version: "24"`, résolu `24.18.0` en pratique) et l'image Docker frontend (`node:24-alpine`, pinnée digest → `24.18.0`) satisfont déjà cette contrainte. **Aucun changement d'infrastructure requis.**

### 2.2 Breaking changes Angular 21→22 (changelog officiel `angular/angular`) confrontés au code actuel

| Breaking change (v22.0.0) | Applicable à LoyerTracker ? |
|---|---|
| TypeScript < 6.0 non supporté | **Oui, obligatoire** — le bump `typescript` → `6.0.x` fait partie du lot |
| `min`/`max` des `Validators` n'acceptent plus de chaînes de caractères | Non — toutes les occurrences de `Validators.min(...)` du code (`dashboard.component.ts` ×2 bailleur + ×2 gestionnaire, `garanties-bail.component.ts` ×3, `paiements-bien.component.ts` ×1) passent déjà des nombres |
| `ComponentFactoryResolver` / `ComponentFactory` / `createNgModuleRef()` / `ChangeDetectorRef.checkNoChanges()` retirés de l'API publique | Non — recherche exhaustive sur `frontend/src` : aucune occurrence |
| `provideRoutes()` retiré | Non — le routing utilise déjà `provideRouter()` |
| `CanMatchFn` exige `currentSnapshot` / `paramsInheritanceStrategy` par défaut change | Non — aucun `CanMatchFn` dans le code |
| Intégration Hammer.js retirée | Non — aucune dépendance Hammer |
| Les attributs `data-*` ne bindent plus les inputs/outputs | Non — les seules occurrences `[data-...]` du code sont des sélecteurs CSS (`.badge[data-statut='...']`), pas des bindings de propriété Angular |
| **Composants sans `changeDetection` explicite basculent par défaut en `OnPush`** | **Oui, applicable** — les 10 composants du projet n'ont aucun `changeDetection` explicite → analyse dédiée ci-dessous |

### 2.3 Point d'attention — bascule implicite vers `OnPush`

Le point le plus sensible fonctionnellement de cette montée de version. Vérification exhaustive du code réalisée le 2026-07-06 :

- Aucune mutation de champ de classe brute trouvée dans les composants (recherche de `this.xxx = ...` hors `.set(`/`.update(`, sur les 10 fichiers `*.component.ts`) : résultat vide sur tous les fichiers.
- Tout l'état affiché en template transite par des **signals** (`signal()`, `.set()`, `.update()`), y compris les données chargées depuis les appels HTTP (ex. `this.typesBiens.set(typesBiens)` dans `bailleur/dashboard/dashboard.component.ts`).
- Les signals déclenchent la vérification de la vue indépendamment de la stratégie de détection de changement — le passage implicite à `OnPush` est donc **à faible risque** sur ce code, contrairement au pattern `this.champ = valeur` dans un callback `.subscribe()` (qui, lui, casserait silencieusement sous `OnPush`).
- **Reste à valider en conditions réelles**, non déductible par analyse statique seule : rendu effectif de chaque écran après upgrade, en particulier les dashboards bailleur/gestionnaire (les plus denses en état). Voir critère GO §4.5.

### 2.4 `keycloak-angular` 20 → 22

Le suivi différé documenté en mémoire projet (`keycloak-angular-functional-api-migration`, issu de la montée Angular 18→19 du 2026-06-06) indiquait encore un usage de la `KeycloakService` legacy à migrer avant sa suppression en v20+. **Vérification 2026-07-06 : mémoire obsolète.** Le code actuel (`app.config.ts`, `core/auth/auth.guard.ts`) est déjà entièrement sur l'API fonctionnelle (`provideKeycloak`, `includeBearerTokenInterceptor`, `createInterceptorCondition`, `createAuthGuard`) — migré lors d'un lot antérieur non retracé dans cette mémoire. **Aucune migration de code d'authentification n'est donc requise** pour ce bump ; seul le conflit de peer dependency (`@angular/common@^22`) doit être résolu par la montée Angular elle-même. Mémoire à corriger en conséquence.

### 2.5 `eslint` — exclu du périmètre de ce lot

`angular-eslint@22.0.0` (version requise par `@angular/cli@22`) accepte `eslint@^9.0.0 || ^10.0.0` — **le bump ESLint 9→10 n'est pas nécessaire** pour cette montée de version. Recommandation : ne pas embarquer la PR #190 dans ce lot ; la traiter séparément plus tard (ou la clore si elle ne devient jamais nécessaire), pour réduire la surface de risque du sprint.

## 3. Périmètre proposé (paquets à faire évoluer ensemble, dans une unique PR)

| Paquet | Actuel | Cible |
|---|---|---|
| `@angular/animations`, `common`, `compiler`, `core`, `forms`, `platform-browser`, `platform-browser-dynamic`, `router` | 20.3.25 | 22.0.5 |
| `@angular/cli`, `@angular-devkit/build-angular`, `@angular/compiler-cli` | 20.3.30 / 20.3.25 | 22.0.5 |
| `typescript` | 5.8.3 | 6.0.x |
| `angular-eslint` | ^20.7.0 | 22.0.0 |
| `keycloak-angular` | 20.1.0 | 22.0.0 |

Hors périmètre : `eslint` (reste `^9.39.4`, cf. §2.5), `rxjs`, `zone.js`, `tslib`, `keycloak-js` — aucune contrainte de peer dependency ne force leur évolution.

## 4. Critères GO / NO GO de fin de sprint

1. `npm install` sans `--legacy-peer-deps` ni `--force` (résolution de dépendances propre).
2. `ng lint` vert.
3. `ng build --configuration production` vert.
4. `ng test` (Karma headless) — 100 % verts, en particulier les specs des dashboards bailleur/gestionnaire.
5. **Vérification navigateur manuelle** des écrans les plus denses en état (dashboards bailleur/gestionnaire, alertes, paiements, garanties) : confirmer visuellement que les listes et formulaires se rafraîchissent correctement après une action asynchrone — couvre le risque résiduel `OnPush` non détectable par les seuls tests headless (§2.3).
6. CI complète verte (Backend inchangé attendu, Frontend, Sécurité gitleaks+SCA+Trivy, CodeQL, Packaging Docker).
7. Aucune régression du parcours métier S01→S04 : à rejouer via `infra/smoke/smoke-stack.sh` avant toute promotion Staging.

## 5. Gouvernance et sort des PR dependabot concernées

- Les PR **#186, #188, #189, #190** restent ouvertes, non mergées individuellement, jusqu'à l'exécution de ce lot.
- À l'issue du sprint, une unique PR portera le bump coordonné du §3 ; son merge est attendu pour rendre les 4 PR dependabot obsolètes (fermeture automatique par dependabot, même mécanisme que #193 → #194 le 2026-07-06).
- La PR **#187** (`@types/jasmine` 5→6, déjà verte et indépendante des paquets Angular) peut être mergée à tout moment, avant ou après ce lot — aucune dépendance croisée.
- Aucun code ne doit être produit avant **GO explicite du PO** sur ce cadrage, conformément à CLAUDE.md (« Démarrer un Sprint ou un Hotfix sans plan ou justification de gouvernance » est interdit).

## 6. Risque résiduel identifié

Ajouté au registre `docs/project-state.md` §13 : bascule implicite vers `OnPush` (Angular 22) non vérifiée en conditions réelles avant le GO de ce sprint — mitigation portée par le critère GO n°5 ci-dessus.
