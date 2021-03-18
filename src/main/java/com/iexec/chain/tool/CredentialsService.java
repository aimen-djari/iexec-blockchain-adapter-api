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

package com.iexec.chain.tool;

import com.iexec.common.chain.CredentialsAbstractService;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;

@Service
@Getter
public class CredentialsService extends CredentialsAbstractService {

    private final String walletPassword;

    public CredentialsService(
            @Value("${wallet.password}") String walletPassword,
            @Value("${wallet.path}") String walletPath
    ) throws Exception {
        super(walletPassword, walletPath);
        this.walletPassword = walletPassword;
    }

    public String getWalletPassword() {
        return this.walletPassword;
    }
}
