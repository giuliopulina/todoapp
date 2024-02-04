package net.giuliopulina.stratospheric.collaboration;

import net.giuliopulina.stratospheric.todo.Todo;
import net.giuliopulina.stratospheric.person.Person;
import org.springframework.data.repository.CrudRepository;

public interface TodoCollaborationRequestRepository extends CrudRepository<TodoCollaborationRequest, Long> {
  TodoCollaborationRequest findByTodoAndCollaborator(Todo todo, Person person);
  TodoCollaborationRequest findByTodoIdAndCollaboratorId(Long todoId, Long collaboratorId);
}