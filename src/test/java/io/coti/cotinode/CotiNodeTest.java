package io.coti.cotinode;

import io.coti.cotinode.crypto.CryptoUtils;
import io.coti.cotinode.data.BaseTransactionData;
import io.coti.cotinode.data.ConfirmationData;
import io.coti.cotinode.data.Hash;
import io.coti.cotinode.http.AddTransactionRequest;
import io.coti.cotinode.http.AddTransactionResponse;
import io.coti.cotinode.http.HttpStringConstants;
import io.coti.cotinode.model.ConfirmedTransactions;
import io.coti.cotinode.model.UnconfirmedTransactions;
import io.coti.cotinode.service.interfaces.IBalanceService;
import io.coti.cotinode.service.interfaces.ITransactionService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = AppConfig.class)
@Slf4j
public class CotiNodeTest {

    private final static String signatureMessage = "message";
    @Autowired
    private ITransactionService transactionService;
    @Autowired
    private ConfirmedTransactions confirmedTransactions;
    @Autowired
    private UnconfirmedTransactions unconfirmedTransactions;
    @Autowired
    private IBalanceService balanceService;
    private int privatekeyInt = 122;

    @BeforeClass
    public static void init() {
        BalanceServiceTests.deleteRocksDBfolder();
    }

    /*
       This is a good scenario where amount and address are dynamically generated
      */
    @Test
    public void aTestFullProcess() {


        AddTransactionRequest addTransactionRequest = new AddTransactionRequest();
        List<BaseTransactionData> baseTransactionDataList = createBaseTransactionRandomList(3);

        addTransactionRequest.baseTransactions = baseTransactionDataList;
        addTransactionRequest.transactionHash = new Hash("A1");
        addTransactionRequest.message = signatureMessage;

        ResponseEntity<AddTransactionResponse> responseEntity = transactionService.addNewTransaction(addTransactionRequest);
        Assert.assertTrue(responseEntity.getStatusCode().equals(HttpStatus.CREATED));
        Assert.assertTrue(responseEntity.getBody().getStatus().equals(HttpStringConstants.STATUS_SUCCESS));

        AddTransactionRequest addTransactionRequest2 = new AddTransactionRequest();
        List<BaseTransactionData> baseTransactionDataList2 = createBaseTransactionRandomList(3);

        addTransactionRequest2.baseTransactions = baseTransactionDataList2;
        addTransactionRequest2.transactionHash = new Hash("A2");
        addTransactionRequest2.message = signatureMessage;
        ResponseEntity<AddTransactionResponse> responseEntity2 = transactionService.addNewTransaction(addTransactionRequest2);
        Assert.assertTrue(responseEntity2.getStatusCode().equals(HttpStatus.CREATED));
        Assert.assertTrue(responseEntity2.getBody().getStatus().equals(HttpStringConstants.STATUS_SUCCESS));

        AddTransactionRequest addTransactionRequest3 = new AddTransactionRequest();
        List<BaseTransactionData> baseTransactionDataList3 = createBaseTransactionRandomList(3);

        addTransactionRequest3.baseTransactions = baseTransactionDataList3;
        addTransactionRequest3.transactionHash = new Hash("A3");
        addTransactionRequest3.message = signatureMessage;
        ResponseEntity<AddTransactionResponse> responseEntity3 = transactionService.addNewTransaction(addTransactionRequest3);
        Assert.assertTrue(responseEntity3.getStatusCode().equals(HttpStatus.CREATED));
        Assert.assertTrue(responseEntity3.getBody().getStatus().equals(HttpStringConstants.STATUS_SUCCESS));


        try {
            log.info("CotiNodeTest is going to sleep for 20 sec");
            TimeUnit.SECONDS.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        ConfirmationData confirmedData = confirmedTransactions.getByHash(new Hash("A1"));

        ConfirmationData unconfirmedData = unconfirmedTransactions.getByHash(new Hash("A1"));

        Assert.assertNotNull(confirmedData);
        Assert.assertNull(unconfirmedData);

    }

    @Test
    public void bTestBadScenarioNewTransactionNegativeAmount() {
        String baseTransactionHexaAddress = TestUtils.getRandomHexa();
        Hash fromAddress = new Hash(TestUtils.getRandomHexa());

        log.info("Base transaction {} with amount {} about to be sent ", baseTransactionHexaAddress, 3);
        replaceBalancesWithAmount(fromAddress,2.0);
        ResponseEntity<AddTransactionResponse> badResponseEntity = transactionService.
                addNewTransaction(createRequestWithOneBaseTransaction(new Hash("AB"),fromAddress,
                        new Hash(baseTransactionHexaAddress), 3.0));
        Assert.assertTrue(badResponseEntity.getStatusCode().equals(HttpStatus.UNAUTHORIZED));
        Assert.assertTrue(badResponseEntity.getBody().getStatus().equals(HttpStringConstants.STATUS_ERROR));

    }


    @Test
    public void cTestBadScenarioNotEnoughSourcesForTcc() {
        Hash fromAddress = new Hash(TestUtils.getRandomHexa());
        updateBalancesWithAddressAndAmount(fromAddress, 100.0);
        String baseTransactionHexaAddress = TestUtils.getRandomHexa();
        double plusAmount = 50;
        log.info("Base transaction {} with amount {} about to be sent ", baseTransactionHexaAddress, plusAmount);
        ResponseEntity<AddTransactionResponse> goodResponseEntity = transactionService.
                addNewTransaction(createRequestWithOneBaseTransaction(new Hash("AC"), fromAddress
                        , new Hash(baseTransactionHexaAddress), plusAmount));
        Assert.assertTrue(goodResponseEntity.getStatusCode().equals(HttpStatus.CREATED));
        Assert.assertTrue(goodResponseEntity.getBody().getStatus().equals(HttpStringConstants.STATUS_SUCCESS));

        String baseTransactionHexaAddress2 = TestUtils.getRandomHexa();
        replaceBalancesWithAmount(fromAddress, 50.0);
        log.info("Base transaction {} with amount {} about to be sent ", baseTransactionHexaAddress2, 60);
        ResponseEntity<AddTransactionResponse> badResponseEntity = transactionService.
                addNewTransaction(createRequestWithOneBaseTransaction(new Hash("AD"),
                        fromAddress, new Hash(baseTransactionHexaAddress), 60.0));
        Assert.assertTrue(badResponseEntity.getStatusCode().equals(HttpStatus.UNAUTHORIZED));
        Assert.assertTrue(badResponseEntity.getBody().getStatus().equals(HttpStringConstants.STATUS_ERROR));

        ConfirmationData confirmationData = confirmedTransactions.getByHash(new Hash("AD"));
        Assert.assertNull(confirmationData);

        try {
            log.info("CotiNodeTest is going to sleep for 20 sec");
            TimeUnit.SECONDS.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        confirmationData = confirmedTransactions.getByHash(new Hash("AC"));
        Assert.assertNull(confirmationData); // The transaction doesn't have enough sources before it


    }

    private void updateBalancesWithAddressAndAmount(Hash hash, Double amount) {
        Map<Hash, Double> balanceMap = balanceService.getBalanceMap();
        Map<Hash, Double> preBalanceMap = balanceService.getPreBalanceMap();
        if (balanceMap.containsKey(hash)) {
            balanceMap.put(hash, amount + balanceMap.get(hash));
        } else {
            balanceMap.put(hash, amount);
        }
        if (preBalanceMap.containsKey(hash)) {
            preBalanceMap.put(hash, amount + preBalanceMap.get(hash));
        } else {
            preBalanceMap.put(hash, amount);
        }
    }

    private void replaceBalancesWithAmount(Hash hash, Double amount) {
        Map<Hash, Double> balanceMap = balanceService.getBalanceMap();
        Map<Hash, Double> preBalanceMap = balanceService.getPreBalanceMap();

        balanceMap.put(hash, amount);
        preBalanceMap.put(hash, amount);

    }

    private AddTransactionRequest createRequestWithOneBaseTransaction(Hash transactionHash, Hash fromAddress, Hash baseTransactionAddress, Double amount) {
        List<BaseTransactionData> baseTransactionDataList = new LinkedList<>();

        BaseTransactionData baseTransactionData =
                new BaseTransactionData(new Hash(CryptoUtils.getPublicKeyFromPrivateKey(BigInteger.valueOf(123)).toByteArray()),
                        amount, baseTransactionAddress,
                        CryptoUtils.getSignatureStringFromPrivateKeyAndMessage(BigInteger.valueOf(123), signatureMessage));


        BaseTransactionData myBaseTransactionData =
                new BaseTransactionData(fromAddress, -1.0 * amount
                        , new Hash("AB"),
                        CryptoUtils.getSignatureStringFromPrivateKeyAndMessage(BigInteger.valueOf(123), signatureMessage));


        baseTransactionDataList.add(baseTransactionData);
        baseTransactionDataList.add(myBaseTransactionData);


        AddTransactionRequest addTransactionRequest = new AddTransactionRequest();
        addTransactionRequest.baseTransactions = baseTransactionDataList;
        addTransactionRequest.transactionHash = transactionHash;
        addTransactionRequest.message = signatureMessage;
        return addTransactionRequest;
    }


    private List<BaseTransactionData> createBaseTransactionRandomList(int numOfBaseTransactions) {
        List<BaseTransactionData> baseTransactionDataList = new LinkedList<>();
        Hash myAddress = new Hash(TestUtils.getRandomHexa());
        updateBalancesWithAddressAndAmount(myAddress, 100.0 * numOfBaseTransactions);

        for (int i = 0; i < numOfBaseTransactions; i++) {
            privatekeyInt++;
            Double amount = TestUtils.getRandomDouble();
            BigInteger privateKey = BigInteger.valueOf(privatekeyInt);
            BaseTransactionData baseTransactionData =
                    new BaseTransactionData(new Hash(CryptoUtils.getPublicKeyFromPrivateKey(privateKey).toByteArray()), amount
                            , new Hash(TestUtils.getRandomHexa()),
                            CryptoUtils.getSignatureStringFromPrivateKeyAndMessage(privateKey, signatureMessage));

            BaseTransactionData myBaseTransactionData =
                    new BaseTransactionData(myAddress, -1.0 * amount
                            , myAddress,
                            CryptoUtils.getSignatureStringFromPrivateKeyAndMessage(privateKey, signatureMessage));


            baseTransactionDataList.add(baseTransactionData);
            baseTransactionDataList.add(myBaseTransactionData);

        }
        return baseTransactionDataList;
    }


}
