/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.blockchain.broker;

import com.iexec.blockchain.tool.ChainConfig;
import com.iexec.blockchain.tool.IexecHubService;
import com.iexec.common.chain.ChainAccount;
import com.iexec.common.sdk.broker.BrokerOrder;
import com.iexec.common.sdk.cli.FillOrdersCliOutput;
import com.iexec.common.sdk.order.payload.AppOrder;
import com.iexec.common.sdk.order.payload.DatasetOrder;
import com.iexec.common.sdk.order.payload.RequestOrder;
import com.iexec.common.sdk.order.payload.WorkerpoolOrder;
import com.iexec.common.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.util.Optional;

@Slf4j
@Service
public class BrokerService {

    private final IexecHubService iexecHubService;
    private final ChainConfig chainConfig;


    public BrokerService(ChainConfig chainConfig, IexecHubService iexecHubService) {
        //TODO Assert broker is up
        this.chainConfig = chainConfig;
        this.iexecHubService = iexecHubService;
    }

    public String matchOrders(BrokerOrder brokerOrder) {
        if (brokerOrder == null) {
            throw new IllegalArgumentException("Broker order cannot be null");
        }
        AppOrder appOrder = brokerOrder.getAppOrder();
        WorkerpoolOrder workerpoolOrder = brokerOrder.getWorkerpoolOrder();
        DatasetOrder datasetOrder = brokerOrder.getDatasetOrder();
        RequestOrder requestOrder = brokerOrder.getRequestOrder();
        if (appOrder == null) {
            throw new IllegalArgumentException("App order cannot be null");
        }
        if (workerpoolOrder == null) {
            throw new IllegalArgumentException("Workerpool order cannot be null");
        }
        if (requestOrder == null) {
            throw new IllegalArgumentException("Request order cannot be null");
        }
        boolean withDataset = !(requestOrder.getDataset().equals(BytesUtils.EMPTY_ADDRESS)
                || StringUtils.isNotEmpty(requestOrder.getDataset()));
        if (datasetOrder == null && withDataset) {
            throw new IllegalArgumentException("Dataset order cannot be null");
        }
        BigInteger datasetPrice = withDataset ? datasetOrder.getDatasetprice() : BigInteger.ZERO;
        if (!hasRequesterAcceptedPrices(brokerOrder.getRequestOrder(),
                appOrder.getAppprice(),
                workerpoolOrder.getWorkerpoolprice(),
                datasetPrice)) {
            throw new IllegalStateException("Incompatible prices");
        }
        //TODO check workerpool stake
        Long deposit = iexecHubService.getChainAccount(requestOrder.getRequester())
                .map(ChainAccount::getDeposit)
                .orElse(-1L);
        if (!hasRequesterDepositedEnough(brokerOrder.getRequestOrder(), deposit)) {
            throw new IllegalStateException("Deposit too low");
        }
        String beneficiary = brokerOrder.getRequestOrder().getBeneficiary();
        log.info("Matching valid orders onchain [requester:{}, beneficiary:{}, " +
                        "pool:{}, app:{}, dataset:{}]", requestOrder.getRequester(), beneficiary,
                workerpoolOrder.getWorkerpool(), appOrder.getApp(), withDataset ? datasetOrder.getDatasetprice() : BigInteger.ZERO);
        return fireMatchOrders(brokerOrder)
                .orElse("");
    }

    private Optional<String> fireMatchOrders(BrokerOrder brokerOrder) {
        try {
            ResponseEntity<FillOrdersCliOutput> responseEntity =
                    new RestTemplate().postForEntity(chainConfig.getBrokerUrl()
                            + "/orders/match", brokerOrder, FillOrdersCliOutput.class);
            if (responseEntity.getStatusCode().is2xxSuccessful()
                    && responseEntity.getBody() != null) {
                FillOrdersCliOutput dealResponse = responseEntity.getBody();
                log.info("Matched orders [chainDealId:{}, tx:{}]", dealResponse.getDealid(), dealResponse.getTxHash());
                return Optional.of(dealResponse.getDealid());
            }
        } catch (Throwable e) {
            log.error("Failed to request match order [requester:{}, app:{}, " +
                            "workerpool:{}, dataset:{}]",
                    brokerOrder.getRequestOrder().getRequester(),
                    brokerOrder.getRequestOrder().getApp(),
                    brokerOrder.getRequestOrder().getWorkerpool(),
                    brokerOrder.getRequestOrder().getDataset(), e);
        }
        return Optional.empty();
    }


    private boolean hasRequesterAcceptedPrices(
            RequestOrder requestOrder,
            BigInteger appPrice,
            BigInteger workerpoolPrice,
            BigInteger datasetPrice
    ) {
        if (requestOrder == null || requestOrder.getWorkerpoolmaxprice() == null
                || requestOrder.getAppmaxprice() == null
                || appPrice == null || workerpoolPrice == null) {
            log.error("Failed to check hasRequesterAcceptedPrices (null requestOrder)");
            return false;
        }
        boolean isAppPriceAccepted = requestOrder.getAppmaxprice().longValue() >= appPrice.longValue();
        boolean isPoolPriceAccepted = requestOrder.getWorkerpoolmaxprice().longValue() >= workerpoolPrice.longValue();
        boolean isDatasetPriceAccepted = requestOrder.getDatasetmaxprice().longValue() >= datasetPrice.longValue();
        boolean isAccepted = isAppPriceAccepted && isPoolPriceAccepted && isDatasetPriceAccepted;
        if (!isAccepted) {
            log.error("Prices not accepted (too expensive) [isAppPriceAccepted:{}, " +
                    "isPoolPriceAccepted:{}]", isAppPriceAccepted, isPoolPriceAccepted);
        }
        return isAccepted;
    }

    private boolean hasRequesterDepositedEnough(RequestOrder requestOrder, long deposit) {
        if (requestOrder == null || requestOrder.getWorkerpoolmaxprice() == null
                || requestOrder.getAppmaxprice() == null) {
            log.error("Failed to check hasRequesterDepositedEnough (null requestOrder)");
            return false;
        }
        long price = requestOrder.getWorkerpoolmaxprice().add(requestOrder.getAppmaxprice()).add(requestOrder.getDatasetmaxprice()).longValue();
        if (price > deposit) {
            log.error("Deposit too low [price:{}, deposit:{}]", price, deposit);
            return false;
        }
        return true;
    }
}
