package com.itgarden.repository;

import com.itgarden.entity.Payment;
import com.itgarden.entity.ProductItem;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment,Long> {

        Payment findPaymentByProductItemAndDeletedFalse(ProductItem productItem);
}
