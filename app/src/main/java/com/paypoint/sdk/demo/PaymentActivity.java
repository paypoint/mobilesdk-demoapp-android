/*
 * Copyright (c) 2015. PayPoint
 */

package com.paypoint.sdk.demo;

import android.app.Fragment;
import android.content.Intent;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;

import com.paypoint.sdk.demo.merchant.MerchantTokenManager;
import com.paypoint.sdk.demo.utils.FontUtils;
import com.paypoint.sdk.demo.widget.CustomMessageDialog;
import com.paypoint.sdk.demo.widget.CustomWaitDialog;
import com.paypoint.sdk.demo.widget.ShakeableEditText;
import com.paypoint.sdk.library.exception.InvalidCredentialsException;
import com.paypoint.sdk.library.exception.PaymentValidationException;
import com.paypoint.sdk.library.exception.TransactionInProgressException;
import com.paypoint.sdk.library.exception.TransactionSuspendedFor3DSException;
import com.paypoint.sdk.library.payment.PaymentError;
import com.paypoint.sdk.library.payment.PaymentManager;
import com.paypoint.sdk.library.payment.PaymentRequest;
import com.paypoint.sdk.library.payment.PaymentSuccess;
import com.paypoint.sdk.library.payment.request.PaymentCard;
import com.paypoint.sdk.library.payment.request.Transaction;
import com.paypoint.sdk.library.security.PayPointCredentials;

import java.util.UUID;

import retrofit.RetrofitError;

public class PaymentActivity extends ActionBarActivity implements PaymentManager.MakePaymentCallback,
    MerchantTokenManager.GetTokenCallback {

    /**
     * The following card numbers can be used for testing against the test payment server:
     * 9900 0000 0000 5159 – returns successful authorisation.
     * 9900 0000 0000 5282 – returns payment declined.
     * All other cards will return a server error.
     *
     */

    private ShakeableEditText editCardNumber;
    private ShakeableEditText editCardExpiry;
    private ShakeableEditText editCardCvv;
    private Button buttonPay;

    private PaymentManager paymentManager;
    private MerchantTokenManager tokenManager;
    private PaymentRequest request;
    private String operationId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment);

        FontUtils.setFontForHierarchy(this, getWindow().getDecorView().findViewById(android.R.id.content));

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.activity_payment_title);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        editCardNumber = (ShakeableEditText)findViewById(R.id.editCardNumber);
        editCardExpiry = (ShakeableEditText)findViewById(R.id.editCardExpiry);
        editCardCvv = (ShakeableEditText)findViewById(R.id.editCardCVV);
        buttonPay = (Button)findViewById(R.id.buttonPay);

        initialiseInlineValidation();

        editCardNumber.addTextChangedListener(new CardNumberFormatter());

        buttonPay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                makePayment();
            }
        });

        // instantiate the PaymentManager in the SDK
        paymentManager = PaymentManager.getInstance(this)
                .setUrl(getString(R.string.url_paypoint));

        tokenManager = new MerchantTokenManager();
    }

    @Override
    protected void onPause() {
        super.onPause();

        paymentManager.lockCallback();
        paymentManager.unregisterPaymentCallback();
    }

    @Override
    protected void onResume() {
        super.onResume();

        paymentManager.registerPaymentCallback(this);
        paymentManager.unlockCallback();
    }

    /**
     * This is an example of how an app might do inline validation of payment fields
     * before the user commits to the payment
     */
    private void initialiseInlineValidation() {

        editCardNumber.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    try {
                        editCardNumber.clearError();
                        paymentManager.validateCardPan(editCardNumber.getText().toString());
                    } catch (PaymentValidationException e) {
                        switch (e.getErrorCode()) {
                            case CARD_PAN_INVALID:
                                editCardNumber.setError(getString(R.string.error_invalid_pan));
                                break;
                            case CARD_PAN_INVALID_LUHN:
                                editCardNumber.setError(getString(R.string.error_invalid_luhn));
                                break;
                        }
                    }
                } else {
                    editCardNumber.showError();
                }
            }
        });

        editCardExpiry.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    try {
                        editCardExpiry.clearError();
                        paymentManager.validateCardExpiry(editCardExpiry.getText().toString());
                    } catch (PaymentValidationException e) {
                        switch (e.getErrorCode()) {
                            case CARD_EXPIRED:
                                editCardExpiry.setError(getString(R.string.error_expired));
                                break;
                            case CARD_EXPIRY_INVALID:
                                editCardExpiry.setError(getString(R.string.error_expiry_invalid));
                                break;
                        }
                    }
                } else {
                    editCardExpiry.showError();
                }
            }
        });

        editCardCvv.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    try {
                        editCardCvv.clearError();
                        paymentManager.validateCardCv2(editCardCvv.getText().toString());
                    } catch (PaymentValidationException e) {
                        switch (e.getErrorCode()) {
                            case CARD_CV2_INVALID:
                                editCardCvv.setError(getString(R.string.error_invalid_cvv));
                                break;
                        }
                    }
                } else {
                    editCardCvv.showError();
                }
            }
        });
    }

    private void makePayment() {

        String cardNumber = editCardNumber.getText().toString();
        String cardExpiry = editCardExpiry.getText().toString();
        String cardCvv = editCardCvv.getText().toString();

        // MERCHANT TO IMPLEMENT - generate this according to your own requirements
        String merchantRef = "mer_" + UUID.randomUUID().toString().substring(0, 8);

        // build up the card payment
        PaymentCard card = new PaymentCard()
                .setPan(cardNumber)
                .setExpiryDate(cardExpiry)
                .setCv2(cardCvv);

        // currency and amount hardcoded in this instance for demo
        Transaction transaction = new Transaction()
                .setCurrency("GBP")
                .setAmount(100.00f)
                .setMerchantReference(merchantRef);

        // create the payment request
        request = new PaymentRequest()
                .setCard(card)
                .setTransaction(transaction);

        try {
            // locally validate payment details entered by user
            paymentManager.validatePaymentDetails(request);

            // start the wait animation - customise this according to your own branding
            onPaymentStarted();

            // MERCHANT TO IMPLEMENT - payment details valid, now get merchant token
            tokenManager.getMerchantToken(getString(R.string.url_merchant), getString(R.string.installation_id), this);

        } catch (PaymentValidationException e) {
            showValidationError(e);
        }
    }

    private void showValidationError(PaymentValidationException e) {

        onPaymentEnded();

        // handle all errors
        String errorMessage = "Unknown error";

        switch (e.getErrorCode()) {
            case CARD_EXPIRED:
                errorMessage = getString(R.string.error_expired);
                break;
            case CARD_EXPIRY_INVALID:
                errorMessage = getString(R.string.error_expiry_invalid);
                break;
            case CARD_PAN_INVALID:
                errorMessage = getString(R.string.error_invalid_pan);
                break;
            case CARD_PAN_INVALID_LUHN:
                errorMessage = getString(R.string.error_invalid_luhn);
                break;
            case CARD_CV2_INVALID:
                errorMessage = getString(R.string.error_invalid_cvv);
                break;
            case NETWORK_NO_CONNECTION:
                errorMessage = getString(R.string.error_no_network);
                break;
            case TRANSACTION_INVALID_AMOUNT:
            case TRANSACTION_INVALID_CURRENCY:
            case INVALID_CREDENTIALS:
            case INVALID_CARD:
            case INVALID_REQUEST:
            case INVALID_TRANSACTION:
            case INVALID_URL:
                errorMessage = "Developer error - check arguments to makePayment";
                break;
        }
        showError(errorMessage);
    }

    /**
     * Callback when token received from merchant server
     * @param token
     */
    @Override
    public void getTokenSucceeded(String token) {

        // create the PayPoint credentials to use for the request
        PayPointCredentials credentials = new PayPointCredentials()
                .setInstallationId(getString(R.string.installation_id))
                .setToken(token);

        paymentManager.setCredentials(credentials);

        try {
            // now make the payment - store the operationId returned, this can be used to check the
            // state of a transaction
            operationId = paymentManager.makePayment(request);
        } catch (PaymentValidationException e) {
            showValidationError(e);
        } catch (TransactionInProgressException e) {
            onPaymentEnded();

            showError("Payment currently in progress");
        } catch (InvalidCredentialsException e) {
            onPaymentEnded();

            showError("Developer error - check arguments to makePayment");
        }
    }

    /**
     * Callback when error receiving token from merchant server
     * @param error
     */
    @Override
    public void getTokenFailed(RetrofitError error) {
        onPaymentEnded();
        showError("Failed to get merchant token");
    }

    /**
     * Callback from PayPoint SDK for payment succeeded
     * @param paymentSuccess
     */
    @Override
    public void paymentSucceeded(final PaymentSuccess paymentSuccess) {
        onPaymentEnded();

        // show receipt activity passing across the paymentSuccess details
        Intent intent = new Intent(PaymentActivity.this, ReceiptActivity.class);
        intent.putExtra(ReceiptActivity.EXTRA_RECEIPT, paymentSuccess);
        finish();
        startActivity(intent);

    }

    /**
     * Callback from PayPoint SDK for payment failed
     * @param paymentError
     */
    @Override
    public void paymentFailed(PaymentError paymentError) {
        onPaymentEnded();

        String errorMessage = "";
        boolean getStatus = false;

        if (paymentError != null) {
            if (paymentError.getKind() == PaymentError.Kind.PAYPOINT) {
                // getReasonMessage() should be used for debugging only - please check PaymentError.ReasonCode

                // PayPointError also provides an error enum
                PaymentError.ReasonCode reasonCode = paymentError.getPayPointError().getReasonCode();

                switch (reasonCode) {

                    case TRANSACTION_TIMED_OUT:
                        getStatus = true;
                        errorMessage = "Transaction timed out wait for a response. Call getPaymentStatus()";
                        break;

                    case TRANSACTION_CANCELLED:
                        errorMessage = "Transaction cancelled by the user";
                        break;

                    case THREE_D_SECURE_TIMEOUT:
                        errorMessage = "Transaction timed out waiting for user to complete 3D Secure";
                        break;

                    case THREE_D_SECURE_ERROR:
                        errorMessage = "An error occurred handling 3D Secure";
                        break;

                    case UNKNOWN:
                        errorMessage = "Something went wrong, we don't know what";
                        break;

                    case INVALID:
                        errorMessage = "Something went wrong, we don't know what";
                        break;

                    case TRANSACTION_DECLINED:
                        errorMessage = "The transaction was declined";
                        break;

                    case SERVER_ERROR:
                        // TODO do we need to check status here
                        errorMessage = "An internal server error occurred";
                        break;

                    case TRANSACTION_NOT_FOUND:
                        errorMessage = "The transaction not found on the server, payment not taken";
                        break;

                    case AUTHENTICATION_FAILED:
                        errorMessage = "The merchant token is incorrect";
                        break;

                    case CLIENT_TOKEN_EXPIRED:
                        errorMessage = "The merchant token has expired";
                        break;

                    case UNAUTHORISED_REQUEST:
                        errorMessage = "The merchant token does not grant you access to making a payment";
                        break;

                }
            } else if (paymentError.getKind() == PaymentError.Kind.NETWORK) {
                errorMessage = "Network error - please retry";
            }
        }

        showError("Payment Failed: \n" + errorMessage, getStatus);
    }

    public void onGetPaymentStatus() {
        try {
            paymentManager.getPaymentStatus(operationId);
            onPaymentStarted();
        } catch (InvalidCredentialsException e) {
            showError("Developer error - check arguments to makePayment");
        } catch (TransactionSuspendedFor3DSException e) {
            showError("Payment suspending for 3D Secure");
        } catch (TransactionInProgressException e) {
            showError("Transaction is in progress, please wait for callback");
        }
    }

    private void showError(String message) {
        showError(message, false);
    }

    private void showError(String message, boolean getStatus) {
        CustomMessageDialog messageDialog = CustomMessageDialog.newInstance("Error", message, getStatus);
        messageDialog.show(getFragmentManager(), "");
    }

    private void onPaymentStarted() {
        // show a wait dialog - this is just a PayPoint branded example!
        CustomWaitDialog waitDialog = CustomWaitDialog.newInstance("Processing...");
        waitDialog.show(getFragmentManager(), "WAIT_DIALOG");
    }

    private void onPaymentEnded() {

        Fragment waitDialog = getFragmentManager().findFragmentByTag("WAIT_DIALOG");

        if (waitDialog != null) {
            getFragmentManager().beginTransaction().remove(waitDialog).commitAllowingStateLoss();
        }
    }
}
