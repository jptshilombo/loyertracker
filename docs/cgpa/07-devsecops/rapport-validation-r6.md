# Rapport de validation runtime R6 — Keycloak OIDC/PKCE et Admin API

Date : 2026-06-07  
Cadre : CGPA v3.0.1 — reserve Gate 6 R6  
Perimetre : stack Docker Compose locale `loyertracker`, point d'entree `https://localhost`.

## Objectif

Valider en runtime :

* le realm Keycloak `loyertracker` ;
* le flux Authorization Code + PKCE S256 du client SPA `loyertracker-spa` ;
* l'appel API protege avec JWT bailleur ;
* le parcours invitation / acceptation gestionnaire utilisant l'Admin API Keycloak.

## Etat runtime avant validation

La stack etait deja active, mais l'image API initiale datait du 2026-06-06 et ne contenait que la migration `V1__init_schema.sql`. Elle ne refletait pas le code courant S02.

Action runtime executee : reconstruction et redemarrage du service `api` uniquement via Docker Compose, sans modification de fichier du depot.

Etat apres rebuild :

* `api` healthy ;
* JAR API reconstruit le 2026-06-07 ;
* 6 repositories JPA detectes ;
* migrations Flyway V1 a V4 validees et appliquees ;
* controleurs S02 et `InvitationController` presents dans le JAR.

## Resultats

| Controle | Resultat | Preuve synthese |
| -------- | -------- | --------------- |
| OIDC discovery | OK | `issuer=https://localhost/auth/realms/loyertracker`, status 200 |
| API health | OK | `/api/actuator/health` status 200, `UP` |
| Client Keycloak `loyertracker-api` inspectable | OK | client trouve dans le realm |
| Configuration client `loyertracker-api` | KO | `bearerOnly=true`, `serviceAccountsEnabled=false` |
| `client_credentials` Admin API | KO | token endpoint status 401 `unauthorized_client` |
| Flux OIDC Authorization Code + PKCE S256 | OK | JWT bailleur obtenu, `aud=loyertracker-api`, role `BAILLEUR`, `expires_in=300` |
| Inscription bailleur API | OK | status 409 attendu car compte de test deja inscrit |
| API protegee `/api/biens` | OK | status 200, liste retournee |
| Emission invitation bailleur | OK | `POST /api/invitations` status 201, token genere |
| Acceptation invitation / Admin API gestionnaire | KO | `POST /api/invitations/{token}/acceptation` status 401 |

## Incoherences detectees

* `.env` contient `KEYCLOAK_API_CLIENT_SECRET`, mais `docker-compose.yml` ne transmet pas `KEYCLOAK_API_CLIENT_SECRET`, `KEYCLOAK_API_CLIENT_ID`, `KEYCLOAK_ADMIN_BASE_URL` ni `KEYCLOAK_REALM` au conteneur `api`.
* Le realm versionne declare `loyertracker-api` comme resource server `bearerOnly`, sans service account. Cette configuration est incompatible avec le flux `client_credentials` attendu par `KeycloakGestionnaireIdentityProvider`.
* Nginx sert encore `infra/nginx/html` comme placeholder. La validation effectuee couvre le flow PKCE/API en runtime, mais pas une navigation Angular build complet servie par Nginx.

## Verdict R6

R6 est partiellement validee, non cloturee.

* GO pour OIDC discovery, PKCE S256, JWT bailleur et appel API protege.
* NO GO pour l'Admin API gestionnaire tant que le client confidentiel/service account et l'injection des variables runtime API ne sont pas corriges.

## Actions correctives recommandees

1. Creer ou convertir un client confidentiel Keycloak dedie a l'Admin API, avec `serviceAccountsEnabled=true`.
2. Attribuer au service account les droits minimaux necessaires a la creation/recherche utilisateur et a l'assignation du role `GESTIONNAIRE`.
3. Injecter les variables runtime dans `api` : `KEYCLOAK_API_CLIENT_ID`, `KEYCLOAK_API_CLIENT_SECRET`, `KEYCLOAK_ADMIN_BASE_URL`, `KEYCLOAK_REALM`.
4. Reexecuter R6 sur le parcours complet invitation -> acceptation -> creation/rattachement gestionnaire.
5. Prevoir une validation separee du build Angular servi par Nginx si l'objectif est de couvrir la SPA packagenee, pas seulement le protocole OIDC/PKCE.

---

## Reexecution R6 — cloture (2026-06-09)

Cadre : Plan d'Execution R6 (Niveau 2) approuve par le PO. Correctif applique sur la branche `fix/r6-keycloak-admin-api`.

### Correctif applique

* **Client confidentiel dedie** `loyertracker-admin` ajoute au realm versionne (`serviceAccountsEnabled=true`), distinct du resource server bearer-only `loyertracker-api` (laisse inchange).
* **Droits service account a privileges minimaux** : roles `realm-management` `manage-users`, `view-users`, `view-realm` (mappes via l'utilisateur de compte de service dans le realm JSON).
* **Secret hors depot** : injecte apres import par le service one-shot `keycloak-init` (`bootstrap-test-account.sh`), jamais versionne dans le realm — meme pattern que le mot de passe du compte de test.
* **Variables runtime injectees dans `api`** : `KEYCLOAK_ADMIN_BASE_URL` (= `http://keycloak:8080/auth`, chemin relatif `/auth` inclus), `KEYCLOAK_REALM`, `KEYCLOAK_API_CLIENT_ID`, `KEYCLOAK_API_CLIENT_SECRET` (docker-compose + `.env.example`).
* **Alignement Spring** : defauts `application.yml` et de l'adaptateur `KeycloakGestionnaireIdentityProvider` corriges (base-url `/auth`, client-id `loyertracker-admin`).

### Anomalie corrigee en cours de validation runtime

L'import du realm echouait initialement (`value too long for type character varying(255)` sur `CLIENT.DESCRIPTION`) : la description du nouveau client depassait 255 caracteres. Description raccourcie (145 car.) — import OK.

### Resultats de la reexecution (stack Docker Compose locale)

| Controle | R6 initial | Reexecution | Preuve |
| -------- | ---------- | ----------- | ------ |
| Token `client_credentials` (`loyertracker-admin`) | KO 401 `unauthorized_client` | **OK 200** | `access_token` Bearer, `expires_in=300` |
| Recherche utilisateur Admin API (`GET /users?email=&exact=true`) | non atteint | **OK 200** | service account autorise |
| Creation utilisateur (`POST /users`) | non atteint | **OK 201** | en-tete `Location` retourne |
| Lecture role realm (`GET /roles/GESTIONNAIRE`) | non atteint | **OK 200** | `view-realm` suffisant |
| Assignation role (`POST /users/{id}/role-mappings/realm`) | non atteint | **OK 204** | `manage-users` suffisant |
| Parcours API complet `POST /api/invitations` puis `.../acceptation` | acceptation KO 401 | **OK 201 + 201** | acceptation pilotee par l'adaptateur reel du conteneur `api` ; gestionnaire cree dans Keycloak avec roles `[default-roles, GESTIONNAIRE]` |

Note de procedure : pour piloter le parcours authentifie en smoke test, le `directAccessGrants` du client `loyertracker-spa` a ete active temporairement (echafaudage runtime, revoque ensuite ; non versionne) afin d'obtenir un JWT bailleur sans navigateur. Le re-import du realm ayant regenere l'id Keycloak du compte de test bailleur, la ligne applicative `bailleur` (creee sous l'ancien sujet) a ete realignee sur le nouveau sujet pour la duree du test — artefact de la procedure de wipe/re-import, sans rapport avec le correctif.

### Verdict R6 (reexecution)

**R6 clotûree — GO.** Les deux conditions NO GO du R6 initial sont levees : (1) le flux `client_credentials` Admin API fonctionne (200) ; (2) l'acceptation d'invitation cree/rattache le gestionnaire et lui assigne le role `GESTIONNAIRE` (201, via l'adaptateur reel). `mvn verify` reste vert (38 tests, 0 echec). Reserve residuelle inchangee : la validation de la SPA Angular servie par Nginx reste hors perimetre R6.
