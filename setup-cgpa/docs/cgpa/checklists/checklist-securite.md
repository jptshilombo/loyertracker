# Checklist sécurité (DevSecOps)

> À dérouler tout au long du projet, en particulier aux **Gate 4 (architecture)**, **Gate 6 (DevSecOps)** et **Gate 8 (production)**.

## 1. Conception (Shift-Left)
- [ ] Analyse de risques / surface d'attaque réalisée
- [ ] Données sensibles identifiées et classifiées (RGPD)
- [ ] Principe du moindre privilège appliqué

## 2. Authentification & autorisation
- [ ] Authentification centralisée (Keycloak / OIDC / OAuth2)
- [ ] Rôles et scopes définis et appliqués côté API
- [ ] Sessions / tokens sécurisés (expiration, rotation)

## 3. Gestion des secrets
- [ ] Aucun secret en clair dans le dépôt
- [ ] Secrets gérés via coffre / variables d'environnement sécurisées
- [ ] Rotation des secrets prévue

## 4. Code & dépendances
- [ ] SAST (analyse statique) intégré à la CI
- [ ] SCA (vulnérabilités des dépendances) intégré
- [ ] Scan de secrets dans la CI
- [ ] Vulnérabilités critiques traitées avant fusion

## 5. Conteneurs & infrastructure
- [ ] Images Docker scannées
- [ ] Images basées sur des sources de confiance, à jour
- [ ] Conteneurs non-root, surface minimale

## 6. Communication & données
- [ ] HTTPS / TLS partout
- [ ] Chiffrement des données sensibles au repos
- [ ] Validation/échappement des entrées (OWASP Top 10)

## 7. Journalisation & supervision
- [ ] Logs de sécurité (auth, erreurs) centralisés
- [ ] Alerting sur événements sensibles
- [ ] Pas de données sensibles dans les logs

## 8. Conformité
- [ ] Conformité RGPD vérifiée
- [ ] Politique de sauvegarde/restauration en place
