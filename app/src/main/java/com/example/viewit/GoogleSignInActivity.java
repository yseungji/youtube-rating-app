package com.example.viewit;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.credentials.CredentialManager;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;

import com.example.viewit.databinding.ActivityGoogleSignInBinding;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.firebase.auth.FirebaseUser;

import static com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL;

import android.util.Log;
import android.widget.Toast;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;


public class GoogleSignInActivity extends AppCompatActivity {

    private ActivityGoogleSignInBinding binding;
    private CredentialManager credentialManager;
    private static final String TAG = "GoogleSignIn";
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAuth = FirebaseAuth.getInstance();
        // 로그인 상태가 유지되어 있으면 로그인 화면을 거치지 않는다.
        if (mAuth.getCurrentUser() != null) {
            openMain();
            return;
        }
        credentialManager = CredentialManager.create(this);
        WindowCompat.enableEdgeToEdge(getWindow());
        binding = ActivityGoogleSignInBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.googleSignIn.setOnClickListener(v -> signIn());
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    private void signIn(){
        launchCredentialManager();
    }

    private void launchCredentialManager() {
        // [START create_credential_manager_request]
        // Instantiate a Google sign-in request
        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.default_web_client_id))
            .build();

        // Create the Credential Manager request
        GetCredentialRequest request = new GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build();
        // [END create_credential_manager_request]

        // Launch Credential Manager UI
        credentialManager.getCredentialAsync(
            this,
            request,
            new CancellationSignal(),
            ContextCompat.getMainExecutor(this),
            new CredentialManagerCallback<>() {
                @Override
                public void onResult(GetCredentialResponse result) {
                    // Extract credential from the result returned by Credential Manager
                    handleSignIn(result.getCredential());
                }

                @Override
                public void onError(GetCredentialException e) {
                    Log.e(TAG, "Couldn't retrieve user's credentials: " + e.getLocalizedMessage());
                    if(e instanceof GetCredentialCancellationException){
                        Toast.makeText(GoogleSignInActivity.this, R.string.google_sign_in_canceled, Toast.LENGTH_SHORT).show();
                    }
                    else {
                        Toast.makeText(GoogleSignInActivity.this, R.string.google_sign_in_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            }
        );
    }

    // [START handle_sign_in]
    private void handleSignIn(Credential credential) {
        // Check if credential is of type Google ID
        if (credential instanceof CustomCredential
            && credential.getType().equals(TYPE_GOOGLE_ID_TOKEN_CREDENTIAL)) {
            // Create Google ID Token
            CustomCredential customCredential = (CustomCredential) credential;

            Bundle credentialData = customCredential.getData();
            GoogleIdTokenCredential googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credentialData);

            // Sign in to Firebase with using the token
            firebaseAuthWithGoogle(googleIdTokenCredential.getIdToken());
        } else {
            Log.w(TAG, "Credential is not of type Google ID!");
            Toast.makeText(GoogleSignInActivity.this, R.string.google_sign_in_failed, Toast.LENGTH_SHORT).show();
        }
    }
    // [END handle_sign_in]
    // [START auth_with_google]
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
            .addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success");
                    FirebaseUser user = mAuth.getCurrentUser();
                    updateUI(user);
                } else {
                    // If sign in fails, display a message to the user
                    Log.w(TAG, "signInWithCredential:failure", task.getException());
                    Toast.makeText(GoogleSignInActivity.this, R.string.google_sign_in_failed, Toast.LENGTH_SHORT).show();
                    updateUI(null);
                }
            });
    }
    // [END auth_with_google]

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            openMain();
        }
    }

    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        // 로그인·테스트 화면을 백스택에서 제거하고 메인을 새 시작점으로 만든다.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
