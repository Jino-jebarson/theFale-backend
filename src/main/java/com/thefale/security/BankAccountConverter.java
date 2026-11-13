package com.thefale.security;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;
@Component
@Converter
public class BankAccountConverter implements AttributeConverter<String, String> {
    private final BankAccountCrypto crypto;
    public BankAccountConverter(BankAccountCrypto crypto) { this.crypto = crypto; }
    public String convertToDatabaseColumn(String value) { return crypto.encrypt(value); }
    public String convertToEntityAttribute(String value) { return crypto.decrypt(value); }
}
