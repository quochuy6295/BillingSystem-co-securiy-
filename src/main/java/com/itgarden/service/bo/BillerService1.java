package com.itgarden.service.bo;

import com.itgarden.common.CodeGenerator;
import com.itgarden.common.TaxCalculation;
import com.itgarden.common.TaxCalculationInput;
import com.itgarden.common.TaxCalculationResponse;
import com.itgarden.common.staticdata.CodeType;
import com.itgarden.common.staticdata.StockStatus;
import com.itgarden.dto.BillerInfo;
import com.itgarden.dto.PaymentInfo;
import com.itgarden.dto.PaymentRequest;
import com.itgarden.entity.*;
import com.itgarden.mapper.BillerMapper;
import com.itgarden.mapper.PaymentMapper;
import com.itgarden.repository.BillerRepository;
import com.itgarden.repository.CustomerRepository;
import com.itgarden.repository.PaymentRepository;
import com.itgarden.repository.ProductItemRepository;
import com.itgarden.service.BaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BillerService1 extends BaseService {

    @Autowired
    private ProductItemRepository productItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private BillerRepository billerRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    private final CodeGenerator codeGenerator;

    private final TaxCalculation taxCalculation;

    public BillerService1(CodeGenerator codeGenerator, TaxCalculation taxCalculation) {
        this.codeGenerator = codeGenerator;
        this.taxCalculation = taxCalculation;
    }

    private Biller sellBiller(Customer customer, int quantity) {
        Biller biller = new Biller();
        biller.setCustomer(customer);
        biller.setQuantity(quantity);
        biller.setBillNo(codeGenerator.newCode(CodeType.BILL_NO));
        Biller newBiller = billerRepository.save(biller);
        return newBiller;
    }

    private TaxCalculationResponse calculateTax(double unitPrice, float taxPercentage, int quantity) {
        TaxCalculationInput taxCalculationInput = new TaxCalculationInput(unitPrice, taxPercentage, quantity);
        taxCalculationInput.setUnitPrice(unitPrice);
        taxCalculationInput.setQuantity(quantity);
        taxCalculationInput.setTaxPercentage(taxPercentage);
        return taxCalculation.calculateTax(taxCalculationInput);
    }

    public BillerInfo save(PaymentRequest paymentRequest) {
        List<String> productItemCode = paymentRequest.getProductItemCode();
        String customerMobileNo = paymentRequest.getCustomerMobileNo();
        int quantity = paymentRequest.getQuantity();
        List<ProductItem> productItems = productItemRepository.findProductItemByProductItemCodeIn(productItemCode);
        Customer customer = customerRepository.findCustomerByMobileNo(customerMobileNo);
        Biller biller = sellBiller(customer, quantity);
        double grandTotal = 0;
        double totalTaxAmount = 0;
        for (ProductItem productItem : productItems) {
            Payment payment = new Payment();
            payment.setProductItem(productItem);
            payment.setProductId(productItem.getId());
            payment.setBiller(biller);
            payment.setTax(productItem.getProduct().getTax());
            TaxCalculationResponse taxCalculationResponse =
                    calculateTax(productItem.getProduct().getPrice(), productItem.getProduct().getTax().getTaxPercentage(), 1);
            payment.setPrice(taxCalculationResponse.getTotalAmount());
            payment.setTaxAmount(taxCalculationResponse.getTaxAmount());
            payment.setTotalPrice(taxCalculationResponse.getTotalAmount() + taxCalculationResponse.getTaxAmount());
            grandTotal = grandTotal + payment.getPrice();
            totalTaxAmount = totalTaxAmount + payment.getTaxAmount();
            paymentRepository.save(payment);
            productItem.setStockStatus(StockStatus.SOLD);
            productItemRepository.save(productItem);
        }
        biller.setGrandTotal(grandTotal);
        biller.setQuantity(quantity);
        biller.setTotalTaxAmount(totalTaxAmount);
        Biller newBiller = billerRepository.save(biller);

        BillerInfo billerInfo = BillerMapper.INSTANCE.billerToBillerInfo(newBiller);
        return billerInfo;
    }

    public List<PaymentInfo> cancle(PaymentRequest paymentRequest){
        List<PaymentInfo> paymentInfos = new ArrayList<>();
        List<String> productItemCodes = paymentRequest.getProductItemCode();
        List<ProductItem> productItems = productItemRepository.findProductItemByProductItemCodeIn(productItemCodes);
        for (ProductItem productItem : productItems) {
            Payment payment = paymentRepository.findPaymentByProductItemAndDeletedFalse(productItem);
            payment.setDeleted(true);
            Payment newPayment = paymentRepository.save(payment);
            Biller biller = payment.getBiller();
            biller.setTotalTaxAmount(biller.getTotalTaxAmount() - payment.getTaxAmount());
            biller.setGrandTotal(biller.getGrandTotal() - payment.getPrice());
            billerRepository.save(biller);
            PaymentInfo paymentInfo = PaymentMapper.INSTANCE.paymentToPaymentInfo(newPayment);
            paymentInfos.add(paymentInfo);
            productItem.setStockStatus(StockStatus.IN_STOCK);
            productItemRepository.save(productItem);
        }
    }
}
