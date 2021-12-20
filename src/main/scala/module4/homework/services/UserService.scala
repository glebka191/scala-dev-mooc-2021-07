package module4.homework.services

import cats.Traverse._
import cats.implicits._
import doobie.quill.DoobieContext
import io.getquill.{CompositeNamingStrategy2, Escape, Literal}
import module4.homework.dao.entity.{Role, RoleCode, User}
import module4.homework.dao.repository.UserRepository
import module4.phoneBook.db.DBTransactor
import zio.interop.catz._
import zio.macros.accessible
import zio.{Has, RIO, ZLayer}

@accessible
object UserService{
    type UserService = Has[Service]

    trait Service{
        def listUsers(): RIO[DBTransactor, List[User]]
        def listUsersDTO(): RIO[DBTransactor, List[UserDTO]]
        def addUserWithRole(user: User, roleCode: RoleCode): RIO[DBTransactor, UserDTO]
        def listUsersWithRole(roleCode: RoleCode): RIO[DBTransactor, List[UserDTO]]
    }

    class Impl(userRepo: UserRepository.Service) extends Service{
        import doobie.implicits._
        val dc: DoobieContext.Postgres[CompositeNamingStrategy2[Escape.type,Literal.type]] = DBTransactor.doobieContext
        import dc._

        def listUsers(): RIO[DBTransactor, List[User]] = for{
             transactor <- DBTransactor.dbTransactor
             users <- userRepo.list().transact(transactor)
        } yield users

        def userRoles(user: User) = for {
            transactor <- DBTransactor.dbTransactor
            roles <- userRepo.userRoles(user.typedId).transact(transactor)
        } yield UserDTO(user, roles.toSet)

        def listUsersDTO(): RIO[DBTransactor,List[UserDTO]] = for {
            users <- listUsers()
            l <- users.map(u => userRoles(u)).traverse(identity)
        } yield l
        
        def addUserWithRole(user: User, roleCode: RoleCode): RIO[DBTransactor,UserDTO] = for {
            transactor <- DBTransactor.dbTransactor
            query = for {
                _ <- userRepo.createUser(user)
                _ <- userRepo.insertRoleToUser(roleCode, user.typedId)
            } yield ()
            _ <- query.transact(transactor)
        } yield UserDTO(user, Set())
        
        def listUsersWithRole(roleCode: RoleCode): RIO[DBTransactor,List[UserDTO]] = for {
            transactor <- DBTransactor.dbTransactor
            users <- userRepo.listUsersWithRole(roleCode).transact(transactor)
            role <- userRepo.findRoleByCode(roleCode).transact(transactor)
        } yield users.map(u => UserDTO(u, role.fold(Set[Role]())(Set[Role](_))))
        
        
    }

    val live: ZLayer[UserRepository.UserRepository, Nothing, UserService] = 
        ZLayer.fromService(repo => new Impl(repo))
}

case class UserDTO(user: User, roles: Set[Role])