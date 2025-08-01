/*
 * Copyright 2019 Web3 Labs Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.web3j.protocol.core.methods.response;

import java.util.Collections;
import java.util.List;

import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.AbiTypes;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.protocol.core.Response;
import org.web3j.utils.EnsUtils;

/** eth_call. */
public class EthCall extends Response<String> {

    // Numeric.toHexString(Hash.sha3("Error(string)".getBytes())).substring(0, 10)
    private static final String ERROR_METHOD_ID = "0x08c379a0";

    @SuppressWarnings("unchecked")
    private static final List<TypeReference<Type>> revertReasonType =
            Collections.singletonList(
                    TypeReference.create((Class<Type>) AbiTypes.getType("string")));

    public String getValue() {
        return getResult();
    }

    public boolean isReverted() {
        if (hasError() && getError().getCode() == 3 && getError().getData() != null) {
            return !EnsUtils.isEIP3668(getError().getData());
        }

        return hasError() || isErrorInResult();
    }

    @Deprecated
    public boolean reverts() {
        return isReverted();
    }

    private boolean isErrorInResult() {
        return getValue() != null && getValue().startsWith(ERROR_METHOD_ID);
    }

    public String getRevertReason() {
        if (isErrorInResult()) {
            String hexRevertReason = getValue().substring(ERROR_METHOD_ID.length());
            List<Type> decoded = FunctionReturnDecoder.decode(hexRevertReason, revertReasonType);
            Utf8String decodedRevertReason = (Utf8String) decoded.get(0);
            return decodedRevertReason.getValue();
        } else if (hasError()) {
            return getError().getMessage();
        }
        return null;
    }

    public String getRevertReasonEncodedData() {
        if (hasError()) {
            return getError().getData();
        }
        return null;
    }
}
