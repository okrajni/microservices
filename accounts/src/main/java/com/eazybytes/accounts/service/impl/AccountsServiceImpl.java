package com.eazybytes.accounts.service.impl;

import com.eazybytes.accounts.constants.AccountsConstants;
import com.eazybytes.accounts.dto.AccountsDto;
import com.eazybytes.accounts.dto.CustomerDto;
import com.eazybytes.accounts.entity.Accounts;
import com.eazybytes.accounts.entity.Customer;
import com.eazybytes.accounts.exception.CustomerAlreadyExistsException;
import com.eazybytes.accounts.exception.ResourceNotFoundException;
import com.eazybytes.accounts.mapper.AccountsMapper;
import com.eazybytes.accounts.mapper.CustomerMapper;
import com.eazybytes.accounts.repository.AccountsRepository;
import com.eazybytes.accounts.repository.CustomerRepository;
import com.eazybytes.accounts.service.IAccountsService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;

@Service
@AllArgsConstructor
public class AccountsServiceImpl implements IAccountsService {

    private AccountsRepository accountsRepository;
    private CustomerRepository customerRepository;

    /**
     * @param customerDto - CustomerDto Object
     */
    @Override
    public void createAccount(CustomerDto customerDto) {
        //Metoda dostaje CustomerDto — to obiekt "transportowy" (Data Transfer Object), używany
        // np. do odbierania danych z requestu HTTP. Tutaj te dane są przepisywane do nowego
        // obiektu Customer, który jest encją (reprezentuje wiersz w bazie danych).
        // Rozdzielenie DTO i encji to częsta praktyka w Javie/Springu — DTO to to, co widzi
        // "świat zewnętrzny", encja to to, co trzyma baza.
        Customer customer = CustomerMapper.mapToCustomer(customerDto, new Customer());
        //customerRepository.findByMobileNumber(...)
        //Sprawdzamy w bazie, czy klient z takim numerem telefonu już istnieje.
        // customerRepository to interfejs (zwykle dziedziczący po JpaRepository),
        // który Spring Data automatycznie implementuje — nie musisz pisać SQL-a,
        // wystarczy nazwa metody.

        //Optional<Customer>
        //To "pojemnik", który może zawierać wynik albo być pusty — zamiast zwracać null i
        // ryzykować NullPointerException. .isPresent() sprawdza, czy coś w nim jest.
        Optional<Customer> optionalCustomer = customerRepository.findByMobileNumber(customerDto.getMobileNumber());

        //Jeśli klient już istnieje → rzucamy wyjątek
        //throw new CustomerAlreadyExistsException(...) przerywa wykonanie metody i zgłasza błąd
        // wyżej (np. do kontrolera, który zwróci odpowiedni kod HTTP,
        // pewnie 400 lub podobny — zależy od obsługi wyjątków w projekcie).
        if(optionalCustomer.isPresent()) {
            throw new CustomerAlreadyExistsException("Customer already registered with given mobileNumber "
                    +customerDto.getMobileNumber());
        }
        //customerRepository.save(customer)
        //Jeśli klienta nie było — zapisujemy go do bazy. Metoda save zwraca zapisany obiekt
        // (z nadanym np. ID).
        Customer savedCustomer = customerRepository.save(customer);
        //Na koniec tworzymy i zapisujemy nowe konto bankowe powiązane z tym klientem
        // (createNewAccount to pewnie prywatna metoda w tej samej klasie, generująca np.
        // losowy numer konta).
        accountsRepository.save(createNewAccount(customer));
    }

    /**
     * @param mobileNumber - Input Mobile Number
     * @return Accounts Details based on a given mobileNumber
     */
    @Override
    public CustomerDto fetchAccount(String mobileNumber) {
        Customer customer = customerRepository.findByMobileNumber(mobileNumber).orElseThrow(
                () -> new ResourceNotFoundException("Customer", "mobileNumber", mobileNumber)
        );
        Accounts accounts = accountsRepository.findByCustomerId(customer.getCustomerId()).orElseThrow(
                () -> new ResourceNotFoundException("Account", "customerID", customer.getCustomerId().toString())
        );

        CustomerDto customerDto = CustomerMapper.mapToCustomerDto(customer, new CustomerDto());
        customerDto.setAccountsDto(AccountsMapper.mapToAccountsDto(accounts, new AccountsDto()));
        return customerDto;
    }

    @Override
    public boolean updateAccount(CustomerDto customerDto) {
        boolean isUpdated = false;
        AccountsDto accountsDto = customerDto.getAccountsDto();
        if(accountsDto !=null ){
            Accounts accounts = accountsRepository.findById(accountsDto.getAccountNumber()).orElseThrow(
                    () -> new ResourceNotFoundException("Account", "AccountNumber", accountsDto.getAccountNumber().toString())
            );
            AccountsMapper.mapToAccounts(accountsDto, accounts);
            accounts = accountsRepository.save(accounts);

            Long customerId = accounts.getCustomerId();
            Customer customer = customerRepository.findById(customerId).orElseThrow(
                    () -> new ResourceNotFoundException("Customer", "CustomerID", customerId.toString())
            );
            CustomerMapper.mapToCustomer(customerDto,customer);
            customerRepository.save(customer);
            isUpdated = true;
        }
        return  isUpdated;
    }

    @Override
    public boolean deleteAccount(String mobileNumber) {
        Customer customer = customerRepository.findByMobileNumber(mobileNumber).orElseThrow(
                () -> new ResourceNotFoundException("Customer", "mobileNumber", mobileNumber)
        );
        accountsRepository.deleteByCustomerId(customer.getCustomerId());
        customerRepository.deleteById(customer.getCustomerId());
        return true;
    }

    /**
     * @param customer - Customer Object
     * @return the new account details
     */
    private Accounts createNewAccount(Customer customer) {
        Accounts newAccount = new Accounts();
        newAccount.setCustomerId(customer.getCustomerId());
        long randomAccNumber = 1000000000L + new Random().nextInt(900000000);

        newAccount.setAccountNumber(randomAccNumber);
        newAccount.setAccountType(AccountsConstants.SAVINGS);
        newAccount.setBranchAddress(AccountsConstants.ADDRESS);
        return newAccount;
    }
}
