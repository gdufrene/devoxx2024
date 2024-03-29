package fr.axa.demo.kafka;

public record UserData (
	String id,
	String mail,
	String firstName,
	String lastName,
	String PhoneNumber
){

	@Override
	public String toString() {
		return """
			UserData
			 🔑 %s
			 🙍 %s %s
			 📧 %s
			 📞 %s
			""".formatted( id, firstName, lastName, mail, PhoneNumber );
	}
	
}
