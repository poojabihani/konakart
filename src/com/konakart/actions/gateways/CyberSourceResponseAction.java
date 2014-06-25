//
// (c) 2006 DS Data Systems UK Ltd, All rights reserved.
//
// DS Data Systems and KonaKart and their respective logos, are 
// trademarks of DS Data Systems UK Ltd. All rights reserved.
//
// The information in this document is free software; you can redistribute 
// it and/or modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
// 
// This software is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// Original version contributed by Chris Derham (Atomus Ltd)
//

package com.konakart.actions.gateways;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.struts2.ServletActionContext;

import com.konakart.al.KKAppEng;
import com.konakart.app.OrderUpdate;
import com.konakart.appif.OrderUpdateIf;
import com.konakart.bl.ConfigConstants;
import com.konakart.bl.modules.payment.cybersource.CyberSource;
import com.konakart.bl.modules.payment.cybersource.CyberSourceHMACTools;

/**
 * This class is an Action class for what to do when a payment result is received from CyberSource.
 * <p>
 * The result could be Authorised, Refused, Cancelled, Pending or Error
 */
public class CyberSourceResponseAction extends BaseGatewayAction
{
    /**
     * The <code>Log</code> instance for this application.
     */
    protected Log log = LogFactory.getLog(CyberSourceResponseAction.class);

    private static HashMap<String, String> reasonsHash = new HashMap<String, String>();

    // Return codes and descriptions
    private static final int RET0 = 0;

    private static final String RET0_DESC = "Transaction OK";

    private static final int RET1 = -1;

    private static final String RET1_DESC = "There was an unexpected problem: ";

    private static final int RET2 = -2;

    private static final String RET2_DESC = "Not Approved: ";

    // Order history comments. These comments are associated with the order.
    private static final String ORDER_HISTORY_COMMENT_OK = "Payment successful. TransactionId = ";

    private static final String ORDER_HISTORY_COMMENT_KO = "Payment not successful. Decison = ";

    private static final long serialVersionUID = 1L;

    public String execute()
    {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        String reconciliationID = null;
        String merchantReference = null;
        String reasonCode = null;
        String ccAuthReply_amount = null;
        String decision = null;
        String ccAuthReply_authorizationCode = null;
        String requestId = null;
        String customerEmail = null;
        String orderAmount_publicSignature = null;
        String orderAmount = null;
        String responseEnvironment = null;

        KKAppEng kkAppEng = null;

        if (log.isDebugEnabled())
        {
            log.debug(CyberSource.CYBERSOURCE_GATEWAY_CODE + " Response Action");
        }

        try
        {
            // Process the parameters sent in the callback
            StringBuffer sb = new StringBuffer();
            if (request != null)
            {
                Enumeration<String> en = request.getParameterNames();
                while (en.hasMoreElements())
                {
                    String paramName = en.nextElement();
                    String paramValue = request.getParameter(paramName);
                    if (sb.length() > 0)
                    {
                        sb.append("\n");
                    }
                    sb.append(paramName);
                    sb.append(" = ");
                    sb.append(paramValue);

                    // Capture important variables so that we can determine whether the transaction
                    // was successful
                    if (paramName != null)
                    {
                        if (paramName.equalsIgnoreCase("decision"))
                        {
                            decision = paramValue;
                        } else if (paramName.equalsIgnoreCase("reconciliationID"))
                        {
                            reconciliationID = paramValue;
                        } else if (paramName.equalsIgnoreCase(CyberSource.CYBERSOURCE_MERCHANT_REF))
                        {
                            merchantReference = paramValue;
                        } else if (paramName.equalsIgnoreCase(CyberSource.CYBERSOURCE_ENVIRONMENT))
                        {
                            responseEnvironment = paramValue;
                        } else if (paramName.equalsIgnoreCase("reasonCode"))
                        {
                            reasonCode = paramValue;
                        } else if (paramName.equalsIgnoreCase("ccAuthReply_amount"))
                        {
                            ccAuthReply_amount = paramValue;
                        } else if (paramName.equalsIgnoreCase("ccAuthReply_authorizationCode"))
                        {
                            ccAuthReply_authorizationCode = paramValue;
                        } else if (paramName.equalsIgnoreCase("requestId"))
                        {
                            requestId = paramValue;
                        } else if (paramName.equalsIgnoreCase("orderAmount"))
                        {
                            orderAmount = paramValue;
                        } else if (paramName.equalsIgnoreCase("CUSTOMER_EMAIL"))
                        {
                            customerEmail = paramValue;
                        } else if (paramName.equalsIgnoreCase("orderAmount_publicSignature"))
                        {
                            orderAmount_publicSignature = paramValue;
                        } else
                        {
                            if (log.isDebugEnabled())
                            {
                                log.debug("Un-processed parameter in response:  '" + paramName
                                        + "' = '" + paramValue + "'");
                            }
                        }
                    }
                }
            }

            String fullGatewayResponse = sb.toString();

            if (log.isDebugEnabled())
            {
                log.debug(CyberSource.CYBERSOURCE_GATEWAY_CODE + " Raw Response data:\n"
                        + fullGatewayResponse);
                log.debug("\n    decision                      = " + decision
                        + "\n    reconciliationID              = " + reconciliationID
                        + "\n    merchantReference             = " + merchantReference
                        + "\n    orderPage_environment         = " + responseEnvironment
                        + "\n    reasonCode                    = " + reasonCode
                        + "\n    ccAuthReply_amount            = " + ccAuthReply_amount
                        + "\n    ccAuthReply_authorizationCode = " + ccAuthReply_authorizationCode
                        + "\n    orderAmount                   = " + orderAmount
                        + "\n    requestId                     = " + requestId
                        + "\n    orderAmount_publicSignature   = " + orderAmount_publicSignature
                        + "\n    customerEmail                 = " + customerEmail);
            }

            // Pick out the values from the merchantReference
            // Split the merchantReference into orderId, orderNumber and store information
            StringTokenizer st = new StringTokenizer(merchantReference, "~");
            int orderId = -1;
            String orderIdStr = null;
            String orderNumberStr = null;
            String storeId = null;
            int engineMode = -1;
            boolean customersShared = false;
            boolean productsShared = false;
            boolean categoriesShared = false;
            String countryCode = null;

            if (st.hasMoreTokens())
            {
                orderIdStr = st.nextToken();
                orderId = Integer.parseInt(orderIdStr);
            }
            if (st.hasMoreTokens())
            {
                orderNumberStr = st.nextToken();
            }
            if (st.hasMoreTokens())
            {
                storeId = st.nextToken();
            }
            if (st.hasMoreTokens())
            {
                engineMode = Integer.parseInt(st.nextToken());
            }
            if (st.hasMoreTokens())
            {
                customersShared = Boolean.getBoolean(st.nextToken());
            }
            if (st.hasMoreTokens())
            {
                productsShared = Boolean.getBoolean(st.nextToken());
            }
            if (st.hasMoreTokens())
            {
                categoriesShared = Boolean.getBoolean(st.nextToken());
            }
            if (st.hasMoreTokens())
            {
                countryCode = st.nextToken();
            }

            if (log.isDebugEnabled())
            {
                log.debug("Derived from merchantReference:         \n"
                        + "    OrderId                       = "
                        + orderId
                        + "\n"
                        + "    OrderNumber                   = "
                        + orderNumberStr
                        + "\n"
                        + "    StoreId                       = "
                        + storeId
                        + "\n"
                        + "    EngineMode                    = "
                        + engineMode
                        + "\n"
                        + "    CustomersShared               = "
                        + customersShared
                        + "\n"
                        + "    ProductsShared                = "
                        + productsShared
                        + "\n"
                        + "    CategoriesShared              = "
                        + categoriesShared
                        + "\n"
                        + "    CountryCode                   = " + countryCode);
            }

            // Get an instance of the KonaKart engine
            // kkAppEng = this.getKKAppEng(request); // v3.2 code
            kkAppEng = this.getKKAppEng(request, response); // v4.1 code

            int custId = this.loggedIn(request, response, kkAppEng, "Checkout");

            // Check to see whether the user is logged in
            if (custId < 0)
            {
                if (log.isInfoEnabled())
                {
                    log.info("Customer is not logged in");
                }
                return KKLOGIN;
            }

            // If we didn't receive a decision, we log a warning and return
            if (decision == null)
            {
                String msg = "No decision returned for the " + CyberSource.CYBERSOURCE_GATEWAY_CODE
                        + " module";
                saveIPNrecord(kkAppEng, orderId, CyberSource.CYBERSOURCE_GATEWAY_CODE,
                        fullGatewayResponse, decision, reconciliationID, RET1_DESC + msg, RET1);
                throw new Exception(msg);
            }

            boolean validateSignature = false;
            String sharedSecret = kkAppEng.getCustomConfig(orderIdStr + "-CUSTOM1", true);
            if (sharedSecret == null)
            {
                if (log.isWarnEnabled())
                {
                    log.warn("CyberSource shared secret is null on the session");
                }
                validateSignature = false;
            } else
            {
                // Check the authenticity of the message by checking the signature
                validateSignature = CyberSourceHMACTools.verifyBase64EncodedSignature(sharedSecret,
                        orderAmount_publicSignature, orderAmount);
            }

            if (log.isDebugEnabled())
            {
                log.debug("Signature Validation Result: " + validateSignature);
            }

            // If the signature on the amount doesn't validate we log a warning and return
            if (!validateSignature)
            {
                String msg = "Signature Validation Failed for the "
                        + CyberSource.CYBERSOURCE_GATEWAY_CODE + " module - orderId " + orderId;
                saveIPNrecord(kkAppEng, orderId, CyberSource.CYBERSOURCE_GATEWAY_CODE,
                        fullGatewayResponse, decision, reconciliationID, RET1_DESC + msg, RET1);
                throw new Exception(msg);
            }

            // Validate we are in the correct environment
            boolean validateEnvironment = true;
            String sessionEnvironment = kkAppEng.getCustomConfig(orderIdStr + "-CUSTOM2", true);
            if (sessionEnvironment == null)
            {
                if (log.isWarnEnabled())
                {
                    log.warn("CyberSource Operating Environment is null on the session");
                }
                validateEnvironment = false;
            } else if (illegalEnvironmentValue(sessionEnvironment))
            {
                if (log.isWarnEnabled())
                {
                    log.warn("CyberSource Operating Environment on the session is illegal");
                }
                validateEnvironment = false;
            } else if (illegalEnvironmentValue(responseEnvironment))
            {
                if (log.isWarnEnabled())
                {
                    log.warn("CyberSource Operating Environment in the session is illegal");
                }
                validateEnvironment = false;
            } else if (!sessionEnvironment.equals(responseEnvironment))
            {
                if (log.isWarnEnabled())
                {
                    log.warn("CyberSource Operating Environment in the session ("
                            + sessionEnvironment + ") does not match that in the response ("
                            + responseEnvironment + ")");
                }
                validateEnvironment = false;
            }

            // If the signature on the amount doesn't validate we log a warning and return
            if (!validateEnvironment)
            {
                String msg = "Environment Validation Failed for the "
                        + CyberSource.CYBERSOURCE_GATEWAY_CODE + " module - orderId " + orderId;
                saveIPNrecord(kkAppEng, orderId, CyberSource.CYBERSOURCE_GATEWAY_CODE,
                        fullGatewayResponse, decision, reconciliationID, RET1_DESC + msg, RET1);
                throw new Exception(msg);
            }

            // See if we need to send an email, by looking at the configuration
            String sendEmailsConfig = kkAppEng.getConfig(ConfigConstants.SEND_EMAILS);
            boolean sendEmail = false;
            if (sendEmailsConfig != null && sendEmailsConfig.equalsIgnoreCase("true"))
            {
                sendEmail = true;
            }

            // If we didn't receive an ACCEPT decision, we let the user Try Again

            OrderUpdateIf updateOrder = new OrderUpdate();
            updateOrder.setUpdatedById(kkAppEng.getActiveCustId());

            if (!decision.equals("ACCEPT"))
            {
                if (log.isDebugEnabled())
                {
                    log.debug("Payment Not Approved for orderId: " + orderId + " for customer: "
                            + customerEmail + " reason: " + getReasonDescription(reasonCode));
                }
                saveIPNrecord(kkAppEng, orderId, CyberSource.CYBERSOURCE_GATEWAY_CODE,
                        fullGatewayResponse, decision, reconciliationID, RET2_DESC + decision
                                + " : " + getReasonDescription(reasonCode), RET2);

                String comment = ORDER_HISTORY_COMMENT_KO + decision;
                kkAppEng.getEng().updateOrder(kkAppEng.getSessionId(), orderId,
                        com.konakart.bl.OrderMgr.PAYMENT_DECLINED_STATUS, sendEmail, comment,
                        updateOrder);
                if (sendEmail)
                {
                    sendOrderConfirmationMail(kkAppEng, orderId, /* success */false);
                }

                String msg = kkAppEng.getMsg("checkout.cc.gateway.error", new String[]
                { comment });
                addActionError(msg);

                return "TryAgain";
            }

            // If successful, we forward to "Approved"

            if (log.isDebugEnabled())
            {
                log.debug("Payment Approved for orderId " + orderId + " for customer "
                        + customerEmail);
            }
            saveIPNrecord(kkAppEng, orderId, CyberSource.CYBERSOURCE_GATEWAY_CODE,
                    fullGatewayResponse, decision, reconciliationID, RET0_DESC, RET0);

            String comment = ORDER_HISTORY_COMMENT_OK + reconciliationID;
            kkAppEng.getEng().updateOrder(kkAppEng.getSessionId(), orderId,
                    com.konakart.bl.OrderMgr.PAYMENT_RECEIVED_STATUS, sendEmail, comment,
                    updateOrder);

            // If the order payment was approved we update the inventory
            kkAppEng.getEng().updateInventory(kkAppEng.getSessionId(), orderId);

            if (sendEmail)
            {
                sendOrderConfirmationMail(kkAppEng, orderId, /* success */true);
            }

            // If we received no exceptions, delete the basket
            kkAppEng.getBasketMgr().emptyBasket();

            return "Approved";

        } catch (Exception e)
        {
            e.printStackTrace();
            return super.handleException(request, e);
        }
    }

    private boolean illegalEnvironmentValue(String environment)
    {
        if (environment == null)
        {
            return true;
        }

        if (environment.equals("TEST") || environment.equals("PRODUCTION"))
        {
            return false;
        }

        if (log.isWarnEnabled())
        {
            log.warn("Illegal Environment : " + environment);
        }
        return true;
    }

    private String getReasonDescription(String code)
    {
        if (code == null)
        {
            return "Unknown reason";
        }

        String reasonDesc = getReasonsHash().get(code);

        if (reasonDesc == null)
        {
            return code;
        }

        return reasonDesc;
    }

    /**
     * @return the reasonsHash
     */
    public static HashMap<String, String> getReasonsHash()
    {
        if (reasonsHash.isEmpty())
        {
            reasonsHash.put("100", "Successful transaction.");
            reasonsHash.put("110", "Authorization was partially approved.");
            reasonsHash.put("150", "Error: General system failure.");
            reasonsHash.put("151",
                    "Error: The request was received, but a server time-out occurred."
                            + " This error does not include time-outs between the "
                            + "client and the server.");
            reasonsHash.put("152", "Error: The request was received, but a service did not finish "
                    + "running in time.");
            reasonsHash.put("200",
                    "The authorization request was approved by the issuing bank but "
                            + "declined by CyberSource because it did not pass the "
                            + "Address Verification Service (AVS) check.");
            reasonsHash.put("201", "The issuing bank has questions about the request. You cannot "
                    + "receive an authorization code in the API reply, but you "
                    + "may receive one verbally by calling the processor.");
            reasonsHash.put("202", "Expired card.");
            reasonsHash.put("203", "The card was declined. No other information provided by the "
                    + "issuing bank.");
            reasonsHash.put("204", "Insufficient funds in the account.");
            reasonsHash.put("205", "The card was stolen or lost.");
            reasonsHash.put("207", "The issuing bank was unavailable.");
            reasonsHash.put("208", "The card is inactive or not authorized for card-not-present "
                    + "transactions.");
            reasonsHash.put("210", "The credit limit for the card has been reached.");
            reasonsHash.put("211", "The card verification number is invalid.");

            reasonsHash.put("220", "The processor declined the request based on a general issue "
                    + "with the customer's account.");
            reasonsHash.put("221",
                    "The customer matched an entry on the processor's negative file.");
            reasonsHash.put("222", "The customer's bank account is frozen.");
            reasonsHash.put("230",
                    "The authorization request was approved by the issuing bank but "
                            + "declined by CyberSource because it did not pass the card "
                            + "verification number check.");
            reasonsHash.put("231", "The account number is invalid.");
            reasonsHash.put("232", "The card type is not accepted by the payment processor.");
            reasonsHash.put("233", "The processor declined the request based on an issue with the "
                    + "request itself.");
            reasonsHash.put("234",
                    "There is a problem with your CyberSource merchant configuration.");
            reasonsHash.put("236", "A processor failure occurred.");
            reasonsHash.put("240",
                    "The card type is invalid or does not correlate with the credit "
                            + "card number.");
            reasonsHash.put("250", "The request was received, but a time-out occurred with the "
                    + "payment processor.");
            reasonsHash.put("475", "The customer is enrolled in payer authentication.");
            reasonsHash.put("476", "The customer cannot be authenticated.");

            reasonsHash
                    .put("520", "The authorization request was approved by the issuing bank but "
                            + "declined by CyberSource based on your Smart Authorization settings.");
        }

        return reasonsHash;
    }
}
