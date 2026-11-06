package br.com.pandecta.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Inicializa o Firebase Admin SDK. Credenciais: arquivo de conta de serviço (FIREBASE_CREDENTIALS_PATH)
 * ou Application Default Credentials (GOOGLE_APPLICATION_CREDENTIALS / ambiente GCP).
 * Para usar os emuladores locais, defina FIRESTORE_EMULATOR_HOST e FIREBASE_AUTH_EMULATOR_HOST.
 */
@Configuration
public class FirebaseConfig {
    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseApp firebaseApp(AppProperties props) throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }
        GoogleCredentials credentials;
        String path = props.firebase().credentialsPath();
        if (path != null && !path.isBlank()) {
            try (InputStream in = new FileInputStream(path)) {
                credentials = GoogleCredentials.fromStream(in);
            }
        } else {
            log.warn("FIREBASE_CREDENTIALS_PATH não definido; usando Application Default Credentials.");
            credentials = GoogleCredentials.getApplicationDefault();
        }
        FirebaseOptions.Builder builder = FirebaseOptions.builder().setCredentials(credentials);
        if (props.firebase().projectId() != null && !props.firebase().projectId().isBlank()) {
            builder.setProjectId(props.firebase().projectId());
        }
        return FirebaseApp.initializeApp(builder.build());
    }

    @Bean
    public FirebaseAuth firebaseAuth(FirebaseApp app) {
        return FirebaseAuth.getInstance(app);
    }

    @Bean
    public Firestore firestore(FirebaseApp app) {
        return FirestoreClient.getFirestore(app);
    }
}
