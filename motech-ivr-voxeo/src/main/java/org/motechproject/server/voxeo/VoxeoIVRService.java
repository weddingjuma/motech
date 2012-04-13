/**
 * MOTECH PLATFORM OPENSOURCE LICENSE AGREEMENT
 *
 * Copyright (c) 2011 Grameen Foundation USA.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Grameen Foundation USA, nor its respective contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY GRAMEEN FOUNDATION USA AND ITS CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL GRAMEEN FOUNDATION USA OR ITS CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
 * OF SUCH DAMAGE.
 */
package org.motechproject.server.voxeo;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.motechproject.ivr.model.CallDetailRecord;
import org.motechproject.ivr.model.CallInitiationException;
import org.motechproject.ivr.service.CallRequest;
import org.motechproject.ivr.service.IVRService;
import org.motechproject.server.voxeo.config.ConfigReader;
import org.motechproject.server.voxeo.config.VoxeoConfig;
import org.motechproject.server.voxeo.dao.AllPhoneCalls;
import org.motechproject.server.voxeo.domain.PhoneCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Voxeo specific implementation of the IVR Service interface
 *
 * Date: 07/03/11
 *
 */
@Component
@Qualifier("VoxeoIVRService")
public class VoxeoIVRService implements IVRService
{
    public static final String APPLICATION_NAME = "applicationName";
    public static final String SUCCESS = "success";
    private static Logger log = LoggerFactory.getLogger(VoxeoIVRService.class);

    @Autowired(required = false)
    private AllPhoneCalls allPhoneCalls;

    private ConfigReader configReader;

    private HttpClient commonsHttpClient;

    private VoxeoConfig voxeoConfig;

    @Autowired
    public VoxeoIVRService(ConfigReader configReader, HttpClient commonsHttpClient) {
        this.configReader = configReader;
        this.commonsHttpClient = commonsHttpClient;
        voxeoConfig = this.configReader.getConfig("/voxeo-config.json");
    }

    @Override
    public void initiateCall(CallRequest callRequest)
    {
        if (callRequest == null) {
            throw new IllegalArgumentException("CallRequest can not be null");
        }

        //Create a call record to track this call
        PhoneCall phoneCall = new PhoneCall(callRequest);
        phoneCall.setDirection(PhoneCall.Direction.OUTGOING);
        phoneCall.setDisposition(CallDetailRecord.Disposition.UNKNOWN);
        phoneCall.setStartDate(new Date());
        allPhoneCalls.add(phoneCall);

        String voxeoURL = voxeoConfig.getServerUrl();
        String tokenId = voxeoConfig.getTokenId(callRequest.getPayload().get(APPLICATION_NAME));

        try {
            HttpMethod httpMethod = generateRequestFor(voxeoURL, callRequest.getPhone(), tokenId, callRequest.getTimeOut());
            int status = commonsHttpClient.executeMethod(httpMethod);
            String response = httpMethod.getResponseBodyAsString();
            log.info("HTTP Status:" + status + "|Response:" + response);

            if (response != null && !response.contains(SUCCESS)) {
                log.info("Could not initiate call :" + response);
                throw new CallInitiationException("Could not initiate call:" + response + " return from Voxeo");
            }
		} catch (MalformedURLException e) {
			log.error("MalformedURLException: ", e);
		} catch (Exception e) {
            log.error("Exception: ", e);
        }
    }

    private HttpMethod generateRequestFor(String voxeoUrl, String phoneNumber, String tokenId, int callTimeOut) {
        GetMethod getMethod = new GetMethod(voxeoUrl);

        List<NameValuePair> queryStringValues = new ArrayList<NameValuePair>();
        queryStringValues.add(new NameValuePair("tokenid", tokenId));
        queryStringValues.add(new NameValuePair("numbertodial", phoneNumber));
        if (0 != callTimeOut) {
            queryStringValues.add(new NameValuePair("calltimeout", Integer.toString(callTimeOut)));
        }
        getMethod.setQueryString(queryStringValues.toArray(new NameValuePair[queryStringValues.size()]));
        return getMethod;
    }
}