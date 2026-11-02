/** A failed device write must never become a successful in-memory record. */
export function commitState(next,previousSerialized,store,key){
 try {const serialized=JSON.stringify(next);store.setItem(key,serialized);return {ok:true,state:next,serialized};}
 catch(error){return {ok:false,state:JSON.parse(previousSerialized),serialized:previousSerialized,error};}
}
