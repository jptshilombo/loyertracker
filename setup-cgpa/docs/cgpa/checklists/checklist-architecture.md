# Checklist architecture

> À dérouler au **Gate 4 (architecture)** — dernier verrou avant le développement.

## 1. Traçabilité
- [ ] Chaque exigence du CDC est couverte par l'architecture
- [ ] Exigences non fonctionnelles adressées (perf, dispo, scalabilité)
- [ ] Décisions documentées (ADR)

## 2. Structure
- [ ] Composants et responsabilités clairement définis
- [ ] Couplage maîtrisé / séparation des préoccupations
- [ ] Modèle de données défini
- [ ] Contrats d'API spécifiés

## 3. Sécurité by design
- [ ] Authentification/autorisation conçue (Keycloak/OIDC)
- [ ] Gestion des secrets prévue
- [ ] Surface d'attaque évaluée
- [ ] Conformité (RGPD) prise en compte

## 4. Déploiement
- [ ] Conteneurisation définie (Docker)
- [ ] Environnements `dev`/`staging`/`prod` prévus
- [ ] Stratégie de déploiement et scalabilité définies

## 5. Qualité & évolutivité
- [ ] Choix technologiques justifiés (stack Spring Boot/Angular)
- [ ] Points d'extension / évolutivité identifiés
- [ ] Risques techniques et mitigations documentés

## 6. Observabilité
- [ ] Stratégie de logs et de monitoring prévue
- [ ] Points de mesure (KPI techniques) définis
