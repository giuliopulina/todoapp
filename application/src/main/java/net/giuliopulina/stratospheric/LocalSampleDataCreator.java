package net.giuliopulina.stratospheric;

import net.giuliopulina.stratospheric.person.Person;
import net.giuliopulina.stratospheric.person.PersonRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Profile("local")
@Component
public class LocalSampleDataCreator implements CommandLineRunner {

    @Autowired
    private PersonRepository personRepository;

    @Override
    public void run(String... args) throws Exception {

        Optional<Person> tom = personRepository.findByEmail("tom@stratospheric.dev");
        if (tom.isEmpty()) {
            Person p1 = new Person();
            p1.setEmail("tom@stratospheric.dev");
            p1.setName("Tom");
            personRepository.save(p1);
        }

        Optional<Person> bjoern = personRepository.findByEmail("bjoern@stratospheric.dev");
        if (bjoern.isEmpty()) {
            Person p1 = new Person();
            p1.setEmail("bjoern@stratospheric.dev");
            p1.setName("Bjoern");
            personRepository.save(p1);
        }

        Optional<Person> philip = personRepository.findByEmail("philip@stratospheric.dev");
        if (philip.isEmpty()) {
            Person p1 = new Person();
            p1.setEmail("philip@stratospheric.dev");
            p1.setName("Philip");
            personRepository.save(p1);
        }
    }
}
