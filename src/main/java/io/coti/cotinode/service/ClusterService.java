package io.coti.cotinode.service;

import io.coti.cotinode.data.Hash;
import io.coti.cotinode.data.TransactionData;
import io.coti.cotinode.model.Transactions;
import io.coti.cotinode.service.interfaces.IClusterService;
import io.coti.cotinode.service.interfaces.ISourceSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service

public class ClusterService implements IClusterService {

    private static Object locker = new Object();
    private final Vector<TransactionData>[] sourceListsByTrustScore = new Vector[101];

    @Value("${cluster.delay.after.tcc}")
    private int delayTimeAfterTccProcess;

    @Autowired
    private QueueService queueService;

    @Autowired
    private Transactions transactions;

    @Autowired
    private ISourceSelector sourceSelector;

    @Autowired
    private TccConfirmationService tccConfirmationService;

    private ConcurrentHashMap<Hash, TransactionData> hashToTccUnconfirmedTransactionsMapping;
    private Executor executor = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void setInitialUnconfirmedTransactions(List<Hash> transactionHashes) {
        for (int i = 0; i <= 100; i++) {
            sourceListsByTrustScore[i] = (new Vector<>());
        }

        hashToTccUnconfirmedTransactionsMapping = new ConcurrentHashMap<>();
        for(Hash transactionHash
                : transactionHashes){
            TransactionData transactionData = transactions.getByHash(transactionHash);
            hashToTccUnconfirmedTransactionsMapping.put(transactionHash, transactionData);
            sourceListsByTrustScore[transactionData.getRoundedSenderTrustScore()].add(transactionData);
        }

        initiateTrustScoreConsensusProcessScheduled();
    }

    private boolean handleUnconfirmedFromQueueTransactions() {
        ConcurrentLinkedQueue<Hash> unconfirmedTransactionsHashFromQueue = queueService.getTccQueue();
        if (unconfirmedTransactionsHashFromQueue.size() > 0) {
            for (Hash hash : unconfirmedTransactionsHashFromQueue) {
                TransactionData transaction = transactions.getByHash(hash);
                attachmentProcess(transaction);
            }
            queueService.removeTccQueue();
            return true;
        }
        return false;
    }

    private void addNewTransactionToMemoryStorage(TransactionData transaction) {
        synchronized (locker) {
            // add to unTccConfirmedTransaction map
            hashToTccUnconfirmedTransactionsMapping.put(transaction.getHash(), transaction);
            //  add to TrustScoreToSourceList map
            addToTrustScoreToSourceListMap(transaction);
        }
    }

    private void addToTrustScoreToSourceListMap(TransactionData transaction) {
        synchronized (locker) {
            if (transaction.isSource()) {
                List<TransactionData> transactionTrustScoreList =
                        sourceListsByTrustScore[transaction.getRoundedSenderTrustScore()];
                if (!transactionTrustScoreList.contains(transaction)) {
                    transactionTrustScoreList.add(transaction);
                }
            }
        }
        // TODO use the TransactionService
    }

    private void deleteTransactionFromHashToUnTccConfirmedTransactionsMapping(Hash hash) {

        synchronized (locker) {
            TransactionData transaction = null;
            if (hashToTccUnconfirmedTransactionsMapping.containsKey(hash)) {
                transaction = hashToTccUnconfirmedTransactionsMapping.get(hash);
                hashToTccUnconfirmedTransactionsMapping.remove(hash);
            }

            removeTransactionFromSourcesList(transaction);
        }
    }

    private void removeTransactionFromSourcesList(TransactionData transaction) {
        sourceListsByTrustScore[transaction.getRoundedSenderTrustScore()].remove(transaction);
    }

    private void attachmentProcess(TransactionData attachedTransactionFromDb) {
        attachedTransactionFromDb.setAttachmentTime(new Date());

        Hash childHash = attachedTransactionFromDb.getHash();
        Hash leftParentHash = attachedTransactionFromDb.getLeftParentHash();
        Hash rightParentHash = attachedTransactionFromDb.getRightParentHash();

        synchronized (locker) {
            attachedTransactionFromDb.setLeftParentHash(leftParentHash);
            attachedTransactionFromDb.setRightParentHash(rightParentHash);
        }

        if (leftParentHash != null && hashToTccUnconfirmedTransactionsMapping.get(leftParentHash) != null) {
            hashToTccUnconfirmedTransactionsMapping.get(leftParentHash).addToChildrenTransactions(childHash);
            removeTransactionFromSourcesList(transactions.getByHash(leftParentHash));
        }

        if (rightParentHash != null && hashToTccUnconfirmedTransactionsMapping.get(rightParentHash) != null) {
            hashToTccUnconfirmedTransactionsMapping.get(rightParentHash).addToChildrenTransactions(childHash);
            removeTransactionFromSourcesList(transactions.getByHash(rightParentHash));
        }

        addNewTransactionToMemoryStorage(attachedTransactionFromDb);

        log.info("transaction with hash:{} attached to cluster, with leftParentHash {} and rightParentHash {}.",childHash ,leftParentHash ,rightParentHash);
    }

    private void initiateTrustScoreConsensusProcessScheduled() {

        executor.execute(() -> {
            while (true) {
                if (handleUnconfirmedFromQueueTransactions())
                {
                    tccConfirmationService.init(hashToTccUnconfirmedTransactionsMapping);
                    tccConfirmationService.topologicalSorting();
                    List<Hash> transactionConsensusConfirmed = tccConfirmationService.setTransactionConsensus();

                    for (Hash hash : transactionConsensusConfirmed) {
                        deleteTransactionFromHashToUnTccConfirmedTransactionsMapping(hash);
                        queueService.addToUpdateBalanceQueue(hash);

                    }
                    try {
                        TimeUnit.SECONDS.sleep(delayTimeAfterTccProcess);
                    } catch (InterruptedException e) {
                        log.error(e.toString());
                    }
                }
            }
        });
    }


    public TransactionData addTransactionDataToSources(TransactionData zeroSpendTransaction) {
        addNewTransactionToMemoryStorage(zeroSpendTransaction);
        log.info("added zeroSpendTransaction with hash:{}", zeroSpendTransaction.getHash());
        return zeroSpendTransaction;
    }

    @Override
    public TransactionData selectSources(TransactionData transactionData) {
        Vector<TransactionData>[] trustScoreToTransactionMappingSnapshot = new Vector[101];
        for (int i = 0; i <= 100; i++) {
            trustScoreToTransactionMappingSnapshot[i] = (Vector<TransactionData>) sourceListsByTrustScore[i].clone();
        }

        List<TransactionData> selectedSourcesForAttachment =
                sourceSelector.selectSourcesForAttachment(
                        trustScoreToTransactionMappingSnapshot,
                        transactionData.getSenderTrustScore());

        if (selectedSourcesForAttachment.size() > 0) {
            transactionData.setLeftParentHash(selectedSourcesForAttachment.get(0).getHash());
        }
        if (selectedSourcesForAttachment.size() > 1) {
            transactionData.setRightParentHash(selectedSourcesForAttachment.get(1).getHash());
        }

        String hashes = "";
        for (TransactionData td: selectedSourcesForAttachment) {
            hashes += td.getHash() + " ";
        }
        log.info("For transaction with hash:{} we found the following sources:{}",transactionData.getHash(), hashes);

        return transactionData;
    }


}
