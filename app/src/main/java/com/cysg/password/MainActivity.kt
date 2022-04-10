package com.cysg.password

import android.app.Activity
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cysg.password.databinding.ActivityMainBinding
import com.cysg.password.databinding.PwdItemListBinding
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding //layout 들이 클래스로 자동 구성됨.
    val RC_SIGN_IN=1000

    //viewModel 사용 => 회전시에도 손실되는 정보가 없게끔.
    private val viewModel:MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        //로그인 안되었을 때
        if(FirebaseAuth.getInstance().currentUser==null){
            login()
        }

        binding.recyclerView.apply{
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = SiteFolderAdapter(
                emptyList(),
                onClickDeleteIcon = {
                    viewModel.deleteFolder(it)
                },
                onClickStarIcon = {
                    viewModel.bookMark(it)
                }
            )
        }

        binding.plusBtn.setOnClickListener {
            val sitefolder=SiteFolder(binding.folderInput.text.toString())
            viewModel.addFolder(sitefolder)

        }

        //관찰 UI 업데이트
        viewModel.siteFolderLiveData.observe(this, Observer {
            (binding.recyclerView.adapter as SiteFolderAdapter).setData(it) //데이터 갱신
        })

        logout_btn.setOnClickListener {
            logout()
        }

        go_web_btn.setOnClickListener {
            val intent:Intent=Intent(Intent.ACTION_VIEW, Uri.parse("http://google.com"))

            startActivity(intent)
        }


    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val response = IdpResponse.fromResultIntent(data)

            if (resultCode == Activity.RESULT_OK) {
                //로그인 성공
                val user = FirebaseAuth.getInstance().currentUser
                viewModel.fetchData()

            } else {
                //로그인 실패
                finish()
            }
        }
    }

    //로그인
    fun login(){
        val providers = arrayListOf(AuthUI.IdpConfig.EmailBuilder().build())

        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .build(),
            RC_SIGN_IN)
    }

    //로그아웃
    fun logout(){
        AuthUI.getInstance()
            .signOut(this)
            .addOnCompleteListener {
                login()
            }
    }
}

//사이트 내용 만들기(text: 폴더내용, star: 즐겨찾기인지 여부)
data class SiteFolder(
    val text: String,
    var star: Boolean = false
)

class SiteFolderAdapter(
    private var myDataset: List<DocumentSnapshot>,
    val onClickDeleteIcon: (sitefolder: DocumentSnapshot) -> Unit,
    val onClickStarIcon: (sitefolder: DocumentSnapshot) -> Unit
) :
    RecyclerView.Adapter<SiteFolderAdapter.SiteFolderViewHolder>() {

    class SiteFolderViewHolder(val binding: PwdItemListBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): SiteFolderAdapter.SiteFolderViewHolder {

        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.pwd_item_list, parent, false)

        return SiteFolderViewHolder(PwdItemListBinding.bind(view))
    }

    override fun onBindViewHolder(holder: SiteFolderViewHolder, position: Int) {
        val sitefolder = myDataset[position]

        holder.binding.folderText.text = sitefolder.getString("text")?:""

        //star가 true 일 때는 즐겨찾기에 추가되는 것.
        if((sitefolder.getBoolean("star")?:false)==true){
            holder.binding.folderText.apply{
                paintFlags=paintFlags or Paint.FAKE_BOLD_TEXT_FLAG
                setTypeface(null,Typeface.BOLD_ITALIC)
            }
        }else{
            holder.binding.folderText.apply{
                paintFlags=0
                setTypeface(null,Typeface.NORMAL)
            }
        }

        //휴지통 이미지 눌렀을때 함수 실행
        holder.binding.deleteBtn.setOnClickListener {
            onClickDeleteIcon.invoke(sitefolder)
        }

        //별표 눌렀을때 즐겨찾기 기능.
        holder.binding.star.setOnClickListener{
            onClickStarIcon.invoke(sitefolder)
        }
    }

    override fun getItemCount() = myDataset.size

    fun setData(newData:List<DocumentSnapshot>){
        myDataset=newData
        notifyDataSetChanged()
    }
}

//viewModel로 화면 회전시에도 뷰가 유지되게끔 함.
class MainViewModel: ViewModel(){

    val db= FirebaseFirestore.getInstance() //DB 코드
    val siteFolderLiveData= MutableLiveData<List<DocumentSnapshot>>() //변경가능한 live data

    init{ //DB 읽기 코드
        fetchData()
    }

    //DB 읽기 코드
    fun fetchData() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            db.collection(user.uid)
                .addSnapshotListener{value, e ->
                    if (e != null) {
                        return@addSnapshotListener
                    }
                    if(value!=null){
                        siteFolderLiveData.value=value.documents
                    }

                }
        }
    }

    fun bookMark(sitefolder: DocumentSnapshot) { //즐겨찾기
        FirebaseAuth.getInstance().currentUser?.let{user->
            val star=sitefolder.getBoolean("star")?:false
            db.collection(user.uid).document(sitefolder.id).update("star",!star)
        }
    }

    fun addFolder(sitefolder: SiteFolder) { //사이트 추가하기 DB에 데이터 쓰기
        FirebaseAuth.getInstance().currentUser?.let{user->
            db.collection(user.uid).add(sitefolder)
        }
    }

    fun deleteFolder(sitefolder: DocumentSnapshot) { //사이트 삭제하기 DB에 데이터 삭제하기
        FirebaseAuth.getInstance().currentUser?.let{user->
            db.collection(user.uid).document(sitefolder.id).delete()
        }
    }
}
