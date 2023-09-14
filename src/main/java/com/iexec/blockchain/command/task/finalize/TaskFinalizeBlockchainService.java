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

package com.iexec.blockchain.command.task.finalize;

import com.iexec.blockchain.command.generic.CommandBlockchain;
import com.iexec.blockchain.tool.IexecHubService;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.ChainTaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.util.Optional;

import static com.iexec.common.utils.DateTimeUtils.now;

@Slf4j
@Service
public class TaskFinalizeBlockchainService implements CommandBlockchain<TaskFinalizeArgs> {

	private final IexecHubService iexecHubService;

	public TaskFinalizeBlockchainService(IexecHubService iexecHubService) {
		this.iexecHubService = iexecHubService;
	}

	@Override
	public boolean canSendBlockchainCommand(TaskFinalizeArgs args) {
		String chainTaskId = args.getChainTaskId();

		Optional<ChainTask> optional = iexecHubService.getChainTask(chainTaskId);
		if (optional.isEmpty()) {
			logError(chainTaskId, args, "blockchain read");
			return false;
		}
		ChainTask chainTask = optional.get();
		
		Optional<ChainDeal> oChainDeal = iexecHubService.getChainDeal(chainTask.getDealid());
		ChainDeal chainDeal = oChainDeal.get();

		if (chainDeal.getChainCategory().getId() == 5 && chainTask.getContributionDeadline() <= now()
				&& chainTask.getStatus().equals(ChainTaskStatus.ACTIVE)) {
			log.info("can send finalize() for a timed out service");
			return true;
		}
		log.info("isService: {}, timeout: {}, now: {}, isActive: {}", chainDeal.getChainCategory().getId() == 5,
				chainTask.getContributionDeadline(), now(), chainTask.getStatus().equals(ChainTaskStatus.ACTIVE));
		
		if (!chainTask.getStatus().equals(ChainTaskStatus.REVEALING)) {
			logError(chainTaskId, args, "task is not revealing");
			return false;
		}
		if (!(now() < chainTask.getFinalDeadline())) {
			logError(chainTaskId, args, "after final deadline");
			return false;
		}
		boolean hasEnoughRevealers = chainTask.getRevealCounter() == chainTask.getWinnerCounter()
				|| (chainTask.getRevealCounter() > 0 && chainTask.getRevealDeadline() <= now());
		if (!hasEnoughRevealers) {
			logError(chainTaskId, args, "not enough revealers");
			return false;
		}
		return true;
	}

	private void logError(String chainTaskId, TaskFinalizeArgs args, String error) {
		log.error("Finalize task blockchain call is likely to revert ({}) " + "[chainTaskId:{}, args:{}]", error,
				chainTaskId, args);
	}

	@Override
	public TransactionReceipt sendBlockchainCommand(TaskFinalizeArgs args) throws Exception {
		return iexecHubService.finalize(args.getChainTaskId(), args.getResultLink(), args.getCallbackData());
	}

}
