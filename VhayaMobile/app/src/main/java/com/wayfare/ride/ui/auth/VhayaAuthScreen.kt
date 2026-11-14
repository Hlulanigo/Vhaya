package com.wayfare.ride.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.wayfare.ride.R
import com.wayfare.ride.domain.AuthState
import com.wayfare.ride.domain.UserSession
import com.wayfare.ride.ui.WayfareUiState
import com.wayfare.ride.ui.WayfareViewModel
import com.wayfare.ride.ui.components.tactilePressScale
import com.wayfare.ride.ui.theme.LocalWayfareColors

private enum class AuthFormMode {
    SIGN_IN,
    CREATE_ACCOUNT,
    RESET_PASSWORD
}

@Composable
fun VhayaAuthScreen(
    uiState: WayfareUiState,
    viewModel: WayfareViewModel,
    modifier: Modifier = Modifier
) {
    val pending = uiState.authState as? AuthState.EmailVerificationPending
    if (pending != null) {
        EmailVerificationScreen(
            session = pending.session,
            busy = uiState.authBusy,
            notice = uiState.authNotice,
            error = uiState.authError,
            onCheckVerified = { viewModel.checkEmailVerified() },
            onResend = { viewModel.resendVerificationEmail() },
            onSignOut = { viewModel.signOut() },
            modifier = modifier
        )
        return
    }

    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current
    val focusManager = LocalFocusManager.current

    var mode by remember { mutableStateOf(AuthFormMode.SIGN_IN) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    fun switchMode(next: AuthFormMode) {
        mode = next
        passwordVisible = false
        viewModel.clearAuthMessages()
    }

    fun submitCurrentForm() {
        focusManager.clearFocus()
        when (mode) {
            AuthFormMode.SIGN_IN -> viewModel.signIn(email, password)
            AuthFormMode.CREATE_ACCOUNT -> viewModel.signUp(name, email, password)
            AuthFormMode.RESET_PASSWORD -> viewModel.sendPasswordReset(email)
        }
    }

    if (mode != AuthFormMode.SIGN_IN) {
        BackHandler { switchMode(AuthFormMode.SIGN_IN) }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = colorScheme.surface,
        unfocusedContainerColor = colorScheme.surface,
        unfocusedBorderColor = colorScheme.outline,
        focusedBorderColor = colorScheme.primary
    )
    val fieldShape = RoundedCornerShape(16.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        val viewportHeight = maxHeight

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 244.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    wayfareColors.walletCardEnd,
                                    wayfareColors.walletCardStart
                                )
                            )
                        )
                ) {
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.16f)
                    ) {
                        val route = Path().apply {
                            moveTo(size.width * 0.82f, size.height * 0.06f)
                            cubicTo(
                                size.width * 0.62f,
                                size.height * 0.34f,
                                size.width * 0.98f,
                                size.height * 0.56f,
                                size.width * 0.80f,
                                size.height * 0.94f
                            )
                        }
                        drawPath(
                            path = route,
                            color = Color.White,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5.dp.toPx(),
                            center = Offset(size.width * 0.82f, size.height * 0.06f)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5.dp.toPx(),
                            center = Offset(size.width * 0.80f, size.height * 0.94f)
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                                modifier = Modifier.size(52.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_vhaya_launcher),
                                    contentDescription = "Vhaya logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Vhaya",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "POLOKWANE · LIMPOPO",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.76f)
                                )
                            }
                        }

                        Spacer(Modifier.height(22.dp))

                        Text(
                            text = "Rides around\nPolokwane.",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = "See fare and pickup details before you book.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.82f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                        Text(
                            text = when (mode) {
                                AuthFormMode.SIGN_IN -> "Welcome back"
                                AuthFormMode.CREATE_ACCOUNT -> "Create your account"
                                AuthFormMode.RESET_PASSWORD -> "Reset your password"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = when (mode) {
                                AuthFormMode.SIGN_IN -> "Sign in to continue with Vhaya."
                                AuthFormMode.CREATE_ACCOUNT -> "Set up your Vhaya account to get started."
                                AuthFormMode.RESET_PASSWORD -> "We’ll send reset instructions to your inbox."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(20.dp))

                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                    label = "authFormFields"
                ) { currentMode ->
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (currentMode == AuthFormMode.CREATE_ACCOUNT) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Full name") },
                                placeholder = { Text("Your full name") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Person, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next
                                ),
                                colors = fieldColors,
                                shape = fieldShape,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("auth_name_input")
                            )
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            placeholder = { Text("you@example.com") },
                            leadingIcon = {
                                Icon(Icons.Filled.Email, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = if (currentMode == AuthFormMode.RESET_PASSWORD) ImeAction.Done else ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    if (currentMode == AuthFormMode.RESET_PASSWORD && !uiState.authBusy) {
                                        submitCurrentForm()
                                    }
                                }
                            ),
                            colors = fieldColors,
                            shape = fieldShape,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_email_input")
                        )

                        if (currentMode != AuthFormMode.RESET_PASSWORD) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password") },
                                    placeholder = {
                                        Text(
                                            if (currentMode == AuthFormMode.SIGN_IN) "Enter your password"
                                            else "At least 6 characters"
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                                    },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { passwordVisible = !passwordVisible },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("auth_toggle_password_visibility")
                                        ) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { if (!uiState.authBusy) submitCurrentForm() }
                                    ),
                                    colors = fieldColors,
                                    shape = fieldShape,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_password_input")
                                )

                                if (currentMode == AuthFormMode.SIGN_IN) {
                                    Row(
                                        horizontalArrangement = Arrangement.End,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        TextButton(
                                            onClick = { switchMode(AuthFormMode.RESET_PASSWORD) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("auth_forgot_password_btn")
                                        ) {
                                            Text(
                                                text = "Forgot password?",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = uiState.authError != null) {
                    uiState.authError?.let { err ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colorScheme.errorContainer,
                            border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = uiState.authNotice != null) {
                    uiState.authNotice?.let { msg ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = wayfareColors.brandHighlight,
                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = ::submitCurrentForm,
                    enabled = !uiState.authBusy,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .tactilePressScale()
                        .testTag("auth_submit_btn")
                ) {
                    if (uiState.authBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            text = when (mode) {
                                AuthFormMode.SIGN_IN -> "Sign in"
                                AuthFormMode.CREATE_ACCOUNT -> "Create account"
                                AuthFormMode.RESET_PASSWORD -> "Send reset link"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Clean bottom link to switch between sign in and sign up
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (mode) {
                        AuthFormMode.SIGN_IN -> {
                            Text(
                                text = "New to Vhaya?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { switchMode(AuthFormMode.CREATE_ACCOUNT) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("auth_mode_create_account")
                            ) {
                                Text(
                                    text = "Create account",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                            }
                        }
                        AuthFormMode.CREATE_ACCOUNT -> {
                            Text(
                                text = "Already have an account?",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = { switchMode(AuthFormMode.SIGN_IN) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("auth_mode_sign_in")
                            ) {
                                Text(
                                    text = "Sign in",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                            }
                        }
                        AuthFormMode.RESET_PASSWORD -> {
                            TextButton(
                                onClick = { switchMode(AuthFormMode.SIGN_IN) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("auth_mode_sign_in")
                            ) {
                                Text(
                                    text = "Back to sign in",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailVerificationScreen(
    session: UserSession,
    busy: Boolean,
    notice: String?,
    error: String?,
    onCheckVerified: () -> Unit,
    onResend: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val wayfareColors = LocalWayfareColors.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(wayfareColors.brandHighlight)
            ) {
                Icon(
                    imageVector = Icons.Filled.MarkEmailRead,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Text(
                text = "Verify your email",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground
            )

            Text(
                text = "We sent a verification link to ${session.email}. Open it in your inbox, then continue below.",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )

            notice?.let {
                Surface(shape = RoundedCornerShape(12.dp), color = wayfareColors.brandHighlight) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            error?.let {
                Surface(shape = RoundedCornerShape(12.dp), color = colorScheme.errorContainer) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = onCheckVerified,
                enabled = !busy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .tactilePressScale()
                    .testTag("verify_email_check_btn")
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = colorScheme.onPrimary
                    )
                } else {
                    Text("I've verified my email", fontWeight = FontWeight.Bold)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onResend,
                    enabled = !busy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Resend")
                }
                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Sign out", color = colorScheme.error)
                }
                    }
                }
            }
        }
    }
}
