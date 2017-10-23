package tech.lapsa.insurance.facade.beans;

import java.time.LocalDate;

import javax.ejb.Stateless;
import javax.inject.Inject;

import com.lapsa.insurance.domain.ContactData;
import com.lapsa.insurance.domain.IdentityCardData;
import com.lapsa.insurance.domain.OriginData;
import com.lapsa.insurance.domain.PersonalData;
import com.lapsa.insurance.domain.ResidenceData;
import com.lapsa.insurance.domain.policy.PolicyDriver;
import com.lapsa.insurance.elements.InsuranceClassType;
import com.lapsa.insurance.elements.InsuredAgeClass;
import com.lapsa.insurance.elements.Sex;
import com.lapsa.kz.idnumber.IdNumbers;

import tech.lapsa.insurance.esbd.NotFound;
import tech.lapsa.insurance.esbd.elements.InsuranceClassTypeService;
import tech.lapsa.insurance.esbd.entities.SubjectPersonEntity;
import tech.lapsa.insurance.esbd.entities.SubjectPersonEntityService;
import tech.lapsa.insurance.facade.PolicyDriverFacade;
import tech.lapsa.java.commons.function.MyOptionals;
import tech.lapsa.java.commons.time.MyTemporals;

@Stateless
public class PolicyDriverFacadeBean implements PolicyDriverFacade {

    @Inject
    private SubjectPersonEntityService subjectPersonService;

    @Inject
    private InsuranceClassTypeService insuranceClassTypeService;

    @Override
    public InsuranceClassType getDefaultInsuranceClass() {
	return insuranceClassTypeService.getDefault();
    }

    @Override
    public PolicyDriver fetchByIdNumber(String idNumber) {
	return MyOptionals.of(idNumber) //
		.flatMap(subjectPersonService::optionalByIIN) //
		.map(this::fetchFrom) //
		.orElse(null);
    }

    @Deprecated
    public void fetch(PolicyDriver driver) {
	clearFetched(driver);
	PolicyDriver fetched = fetchByIdNumber(driver.getIdNumber());

	driver.setFetched(fetched.isFetched());

	driver.setInsuranceClassType(fetched.getInsuranceClassType());
	driver.setAgeClass(fetched.getAgeClass());

	driver.setPersonalData(fetched.getPersonalData());
	driver.setResidenceData(fetched.getResidenceData());
	driver.setOriginData(fetched.getOriginData());
	driver.setIdentityCardData(fetched.getIdentityCardData());
	driver.setTaxPayerNumber(fetched.getTaxPayerNumber());
	driver.setContactData(fetched.getContactData());
    }

    @Deprecated
    public void clearFetched(PolicyDriver driver) {
	driver.setFetched(false);

	driver.setInsuranceClassType(getDefaultInsuranceClass());
	driver.setAgeClass(null);

	driver.setPersonalData(new PersonalData());
	driver.setResidenceData(new ResidenceData());
	driver.setOriginData(new OriginData());
	driver.setIdentityCardData(new IdentityCardData());
	driver.setTaxPayerNumber(null);
	driver.setContactData(new ContactData());
    }

    // PRIVATE

    private PolicyDriver fetchFrom(SubjectPersonEntity esbdEntity) {

	PolicyDriver driver = new PolicyDriver();

	if (esbdEntity != null) {

	    String idNumber = esbdEntity.getIdNumber();

	    if (idNumber != null) {
		driver.setIdNumber(idNumber);
	    }

	    InsuranceClassType insuranceClassTypeLocal = null;
	    {
		insuranceClassTypeLocal = insuranceClassTypeService.getDefault();
		try {
		    insuranceClassTypeLocal = insuranceClassTypeService.getForSubject(esbdEntity);
		} catch (NotFound e) {
		}
	    }

	    LocalDate dobLocal = null;
	    {
		dobLocal = IdNumbers.dateOfBirthFrom(idNumber).orElse(null);
		if (esbdEntity != null && esbdEntity.getPersonal() != null
			&& esbdEntity.getPersonal().getDayOfBirth() != null)
		    dobLocal = esbdEntity.getPersonal().getDayOfBirth();
	    }

	    InsuredAgeClass insuredAgeClassLocal = null;
	    {
		if (dobLocal != null)
		    insuredAgeClassLocal = obtainInsuredAgeClass(dobLocal);
	    }

	    Sex sexLocal = null;
	    {
		sexLocal = convertKZLibSex(IdNumbers.genderFrom(idNumber).orElse(null));
		if (esbdEntity != null && esbdEntity.getPersonal() != null
			&& esbdEntity.getPersonal().getSex() != null)
		    sexLocal = esbdEntity.getPersonal().getSex();
	    }

	    driver.setIdNumber(idNumber);

	    driver.setInsuranceClassType(insuranceClassTypeLocal);
	    driver.setAgeClass(insuredAgeClassLocal);

	    driver.getPersonalData().setDayOfBirth(dobLocal);
	    driver.getPersonalData().setSex(sexLocal);

	    if (esbdEntity != null) {
		driver.setFetched(true);

		if (esbdEntity.getPersonal() != null) {
		    driver.getPersonalData().setName(esbdEntity.getPersonal().getName());
		    driver.getPersonalData().setSurename(esbdEntity.getPersonal().getSurename());
		    driver.getPersonalData().setPatronymic(esbdEntity.getPersonal().getPatronymic());
		}

		if (esbdEntity.getOrigin() != null) {
		    driver.getResidenceData().setResident(esbdEntity.getOrigin().isResident());
		    driver.getOriginData().setCountry(esbdEntity.getOrigin().getCountry());
		}

		if (esbdEntity.getContact() != null)
		    driver.getResidenceData().setAddress(esbdEntity.getContact().getHomeAdress());

		if (esbdEntity.getOrigin().getCity() != null)
		    driver.getResidenceData().setCity(esbdEntity.getOrigin().getCity());

		if (esbdEntity.getIdentityCard() != null) {
		    driver.getIdentityCardData().setNumber(esbdEntity.getIdentityCard().getNumber());
		    driver.getIdentityCardData()
			    .setDateOfIssue(MyTemporals.calendar().toLocalDate(esbdEntity.getIdentityCard().getDateOfIssue()));
		    driver.getIdentityCardData().setType(esbdEntity.getIdentityCard().getIdentityCardType());
		    driver.getIdentityCardData()
			    .setIssuingAuthority(esbdEntity.getIdentityCard().getIssuingAuthority());
		}

		if (esbdEntity.getContact() != null) {
		    driver.getContactData().setEmail(esbdEntity.getContact().getEmail());
		    driver.getContactData().setPhone(esbdEntity.getContact().getPhone());
		    driver.getContactData().setSiteUrl(esbdEntity.getContact().getSiteUrl());
		}

		driver.setTaxPayerNumber(esbdEntity.getTaxPayerNumber());
	    }
	}

	return driver;
    }

    // PRIVATE STATIC

    private static Sex convertKZLibSex(com.lapsa.kz.idnumber.IdNumbers.Gender kzLibSex) {
	if (kzLibSex == null)
	    return null;
	switch (kzLibSex) {
	case FEMALE:
	    return Sex.FEMALE;
	case MALE:
	    return Sex.MALE;
	}
	return null;
    }

    private static InsuredAgeClass obtainInsuredAgeClass(LocalDate dayOfBirth) {
	if (dayOfBirth == null)
	    return null;
	int years = calculateAgeByDOB(dayOfBirth);
	return _obtainInsuredAgeClass(years);
    }

    private static int calculateAgeByDOB(LocalDate dob) {
	if (dob == null)
	    throw new NullPointerException();
	return dob.until(LocalDate.now()).getYears();
    }

    private static InsuredAgeClass _obtainInsuredAgeClass(int years) {
	return years < 25 ? InsuredAgeClass.UNDER25 : InsuredAgeClass.OVER25;
    }

}
