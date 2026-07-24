package com.loyertracker.notifications.provider;

import java.util.Base64;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.loyertracker.notifications.CanalNotification;

/**
 * Implémentation réelle de {@link NotificationProvider} pour Twilio WhatsApp (US-122), en
 * environnement <strong>Sandbox exclusivement</strong> (ADR-18, K4 : réutilisation d'un numéro
 * existant, aucun compte Twilio de Production ce sprint). Appelle directement l'API REST Messages
 * de Twilio (authentification HTTP Basic {@code accountSid:authToken}) via {@link RestClient} — un
 * appel HTTP simple suffit et évite la dépendance au SDK officiel, plus lourde, pour ce seul
 * besoin d'émission. Ne gère jamais le canal {@link CanalNotification#SMS} (US-124, hors périmètre
 * Sprint N+1) : {@link #envoyer} le rejette explicitement.
 *
 * <p>Bean actif uniquement si {@code app.notifications.whatsapp.enabled=true} — en exclusion
 * mutuelle avec {@link NoopNotificationProvider} (même flag, conditions opposées).</p>
 */
@Component
@ConditionalOnProperty(prefix = "app.notifications", name = "whatsapp.enabled", havingValue = "true")
public class TwilioNotificationProvider implements NotificationProvider {

    private static final Logger log = LoggerFactory.getLogger(TwilioNotificationProvider.class);
    private static final String MESSAGES_URL_TEMPLATE =
            "https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json";

    private final RestClient restClient;
    private final String accountSid;
    private final String whatsappFrom;
    private final String statusCallbackUrl;

    public TwilioNotificationProvider(
            @Value("${twilio.account-sid:}") String accountSid,
            @Value("${twilio.auth-token:}") String authToken,
            @Value("${twilio.whatsapp-from:}") String whatsappFrom,
            @Value("${twilio.status-callback-base-url:https://loyertracker.loyerpro.org}") String statusCallbackBaseUrl) {
        this.accountSid = accountSid;
        this.whatsappFrom = whatsappFrom;
        this.statusCallbackUrl = statusCallbackBaseUrl + "/api/public/notifications/callback";
        String basic = Base64.getEncoder()
                .encodeToString((accountSid + ":" + authToken).getBytes());
        this.restClient = RestClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .build();
    }

    @Override
    public ResultatEnvoi envoyer(DemandeEnvoi demande) {
        if (demande.canal() != CanalNotification.WHATSAPP) {
            // US-124 (SMS fallback) hors périmètre Sprint N+1 : jamais un envoi silencieux.
            return new ResultatEnvoi(false, null, "CANAL_NON_SUPPORTE_SPRINT_N1");
        }
        MultiValueMap<String, String> corps = new LinkedMultiValueMap<>();
        corps.add("To", "whatsapp:" + demande.phoneE164());
        corps.add("From", "whatsapp:" + whatsappFrom);
        corps.add("Body", rendre(demande.templateCode(), demande.variables()));
        corps.add("StatusCallback", statusCallbackUrl);
        try {
            TwilioMessageResponse reponse = restClient.post()
                    .uri(MESSAGES_URL_TEMPLATE.formatted(accountSid))
                    .body(corps)
                    .retrieve()
                    .body(TwilioMessageResponse.class);
            if (reponse == null || reponse.sid() == null) {
                return new ResultatEnvoi(false, null, "REPONSE_TWILIO_VIDE");
            }
            return new ResultatEnvoi(true, reponse.sid(), null);
        } catch (RestClientException e) {
            log.warn("Échec d'envoi WhatsApp via Twilio (transitoire) : {}", e.getMessage());
            return new ResultatEnvoi(false, null, "ERREUR_TRANSPORT_TWILIO");
        }
    }

    /**
     * Rendu minimal des variables dans le corps du message (US-122). Le contenu réel du template
     * P0 est administré via {@code NotificationTemplate} ({@code providerTemplateId}) ; en
     * l'absence d'intégration Content API réelle (hors périmètre), le corps est reconstruit ici à
     * partir du code du template et des variables résolues par l'événement d'origine.
     */
    private String rendre(String templateCode, Map<String, String> variables) {
        String corpsVariables = variables.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
        return "[" + templateCode + "] " + corpsVariables;
    }

    private record TwilioMessageResponse(String sid, String status) {
    }
}
